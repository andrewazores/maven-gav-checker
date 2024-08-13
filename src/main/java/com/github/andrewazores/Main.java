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
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
                        + " If the GitHub 'gh' client is installed, this can also be a GitHub Pull"
                        + " Request URL and the PR title will be used to infer the GAV. If 'gh' and"
                        + " 'mvn' are available then this may also be a GitHub repository URL, in"
                        + " which case all dependencies of the 'pom.xml' of the repository's"
                        + " default branch will be checked.")
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

    @ConfigProperty(name = "gav-checker.maven-repository.url")
    String configRepoRoot;

    @Option(
            names = {"-n", "--limit"},
            description =
                    "The number of release versions to list in version listing mode. Defaults to"
                            + " the full list.",
            defaultValue = "-1")
    private int count;

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

    @ConfigProperty(name = "gav-checker.maven-repository.skip-tls-validation")
    boolean configInsecure;

    @Inject @All List<SourceIntegration> sourceIntegrations;
    @Inject Processor processor;

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (insecure || configInsecure) {
            disableTlsValidation();
        }
        if (configRepoRoot != null && !configRepoRoot.isBlank()) {
            repoRoot = configRepoRoot;
        }
        if (repoRoot.endsWith("/")) {
            repoRoot = repoRoot.substring(0, repoRoot.length() - 1);
        }
        Collection<GroupArtifactVersion> dependencies = new CopyOnWriteArrayList<>();
        gavs.forEach(
                gav -> {
                    try {
                        var url = new URL(gav);
                        boolean matched = false;
                        for (var integration : sourceIntegrations) {
                            boolean applies = integration.test(url);
                            matched |= applies;
                            Log.debugv(
                                    "integration {0} applies to {1} -> {2}",
                                    integration.getClass().getName(), url, applies);
                            if (applies) {
                                dependencies.addAll(integration.apply(url));
                                Log.trace(gavs.toString());
                            }
                        }
                        if (!matched) {
                            throw new RuntimeException(
                                    "No matching integrations found for provided URL");
                        }
                    } catch (IOException | InterruptedException mue) {
                        var matcher = GAV_PATTERN.matcher(gav);
                        if (!matcher.matches()) {
                            throw new RuntimeException(
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
        Log.tracev("Processing GAVs: {0}", dependencies);
        return processor.execute(dependencies, repoRoot, count).get();
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
