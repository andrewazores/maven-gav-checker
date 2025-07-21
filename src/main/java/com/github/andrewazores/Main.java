/*
 * Copyright Andrew Azores.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.andrewazores;

import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.github.andrewazores.integrations.SourceIntegration;
import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.output.OutputReporter;
import io.quarkus.arc.All;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "maven-gav-checker",
        mixinStandardHelpOptions = true,
        versionProvider = com.github.andrewazores.VersionProvider.class,
        description = "Check Maven dependencies' availability in a particular Maven repository")
public class Main implements Callable<Integer> {

    private static final Pattern GAV_PATTERN =
            Pattern.compile(
                    "^(?<group>[a-z0-9._-]+):(?<artifact>[a-z0-9._-]+)(?::(?<version>[a-z0-9._-]+))?$",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Parameters(
            index = "0..*",
            description =
                    "List of Maven dependencies in the form of GroupId:ArtifactId[:Version] (GAVs),"
                        + " ex. org.slf4j:slf4j-api:2.0.12 or info.picocli:picocli . If no version"
                        + " is specified (version listing mode) then all available versions are"
                        + " printed, otherwise the existence of the specified version is checked."
                        + " Rather than GAVs, these may also be URLs to pom.xml files, or GitHub"
                        + " Repositories, or GitHub Pull Requests, in which case the tool will"
                        + " infer the relevant GAVs.")
    private List<String> gavs;

    @Option(
            names = {"-r", "--repository"},
            description =
                    "The Maven repository root URL to search, ex."
                        + " https://repo.maven.apache.org/maven2/ . If the configuration property"
                        + " maven-gav-checker.maven-repository.url (or the environment variable"
                        + " MAVEN_GAV_CHECKER_MAVEN_REPOSITORY_URL) is set, that will take"
                        + " precedence over this option.",
            defaultValue = "https://repo.maven.apache.org/maven2/")
    private String repoRoot;

    @ConfigProperty(name = "maven-gav-checker.maven-repository.url")
    String configRepoRoot;

    @Option(
            names = {"-n", "--limit"},
            description =
                    "The number of release versions to list in version listing mode. Defaults to"
                            + " the full list.",
            defaultValue = "-1")
    private int count;

    @Option(
            names = {"-o", "--output-format"},
            description = "The output format to print: human, json, or xml. Defaults to 'human'.",
            defaultValue = "human")
    private String outputFormat;

    @Option(
            names = {"-k", "--insecure"},
            description =
                    "Disable TLS validation on the remote Maven repository. This can also be set"
                            + " with the configuration property"
                            + " maven-gav-checker.maven-repository.skip-tls-validation (or the"
                            + " environment variable"
                            + " MAVEN_GAV_CHECKER_MAVEN_REPOSITORY_SKIP_TLS_VALIDATION).",
            defaultValue = "false")
    private boolean insecure;

    @Option(
            names = {"-i", "--interactive"},
            description =
                    "Run an interactive session so that multiple GAVs can be checked sequentially"
                            + " without re-invoking the tool.",
            defaultValue = "false")
    private boolean interactive;

    @ConfigProperty(name = "maven-gav-checker.maven-repository.skip-tls-validation")
    boolean configInsecure;

    @Inject @All List<SourceIntegration> sourceIntegrations;
    @Inject @All List<OutputReporter> reporters;
    @Inject Processor processor;

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!interactive && (gavs == null || gavs.isEmpty())) {
            throw new IllegalArgumentException("No GAV arguments");
        }
        var reporter =
                reporters.stream()
                        .filter(r -> r.formatSpecifier().equals(outputFormat))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                String.format(
                                                        "Unknown output format \"%s\"",
                                                        outputFormat)));
        if (insecure || configInsecure) {
            disableTlsValidation();
        }
        if (configRepoRoot != null && !configRepoRoot.isBlank()) {
            repoRoot = configRepoRoot;
        }
        if (repoRoot.endsWith("/")) {
            repoRoot = repoRoot.substring(0, repoRoot.length() - 1);
        }
        if (interactive) {
            try (Scanner scanner = new Scanner(System.in)) {
                if (count == -1) {
                    count = 1;
                }
                System.out.print("? ");
                while (scanner.hasNext()) {
                    String tok = scanner.next();
                    System.out.println("...");
                    try {
                        processor.execute(reporter, processGAVs(List.of(tok)), repoRoot, count);
                    } catch (Exception e) {
                        Log.error(e);
                    }
                    System.out.print("? ");
                }
            }
            return 0;
        }
        Collection<GroupArtifactVersion> dependencies = processGAVs(gavs);
        return processor.execute(reporter, dependencies, repoRoot, count);
    }

    private Collection<GroupArtifactVersion> processGAVs(Collection<String> gavs) {
        Collection<GroupArtifactVersion> dependencies = new CopyOnWriteArrayList<>();
        gavs.forEach(
                gav -> {
                    try {
                        var url = new URL(gav);
                        dependencies.addAll(
                                sourceIntegrations.stream()
                                        .filter(integration -> integration.test(url))
                                        .findFirst()
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "No matching integrations found for"
                                                                        + " provided URL"))
                                        .apply(url));
                    } catch (IOException | InterruptedException mue) {
                        var matcher = GAV_PATTERN.matcher(gav);
                        if (!matcher.matches()) {
                            throw new IllegalArgumentException(
                                    String.format("GAV %s was not parseable", gav));
                        } else {
                            var groupId = matcher.group("group");
                            var artifactId = matcher.group("artifact");
                            var version = matcher.group("version");
                            dependencies.add(
                                    new GroupArtifactVersion(groupId, artifactId, version));
                        }
                    }
                });
        assert !dependencies.isEmpty();
        Log.tracev("Processing GAVs: {0}", dependencies);
        return dependencies;
    }

    private void disableTlsValidation() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager trm =
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                };
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, new TrustManager[] {trm}, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }
}
