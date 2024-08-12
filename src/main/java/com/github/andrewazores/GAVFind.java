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

import java.io.BufferedInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "GAVFind",
        mixinStandardHelpOptions = true,
        version = "GAVFind 0.1",
        description = "Check Maven dependencies' availability in a particular Maven repository")
public class GAVFind implements Callable<Integer> {

    private static final Pattern GAV_PATTERN =
            Pattern.compile(
                    "^(?<group>[a-z0-9._-]+):(?<artifact>[a-z0-9._-]+)(?::(?<version>[a-z0-9._-]+))?$",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Parameters(
            index = "0",
            description =
                    "The Maven dependency GroupId:ArtifactId[:Version] (GAV), ex."
                        + " org.slf4j:slf4j-api:2.0.12 or info.picocli:picocli . If no version is"
                        + " specified (version listing mode) then all available versions are"
                        + " printed, otherwise the existence of the specified version is checked."
                        + " If the GitHub 'gh' client is installed, this can also be a GitHub Pull"
                        + " Request path and the PR title will be used to infer the GAV.")
    private String gav;

    @Option(
            names = {"-r", "--repository"},
            description =
                    "The Maven repository root URL to search, ex."
                            + " https://repo.maven.apache.org/maven2/",
            defaultValue = "https://repo.maven.apache.org/maven2/")
    private String repoRoot;

    @Option(
            names = {"-c", "--count"},
            description =
                    "The number of release versions to list in version listing mode. Defaults to"
                            + " the full list",
            defaultValue = "-1")
    private int count;

    @Option(
            names = {"-k", "--insecure"},
            description = "Disable TLS validation",
            defaultValue = "false")
    private boolean insecure;

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose debugging output",
            defaultValue = "false")
    private boolean verbose;

    private final GitHubIntegration gitHubIntegration = GitHubIntegration.newInstance();

    public static void main(String... args) {
        int exitCode = new CommandLine(new GAVFind()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (insecure) {
            disableTlsValidation();
        }
        if (repoRoot.endsWith("/")) {
            repoRoot = repoRoot.substring(0, repoRoot.length() - 1);
        }
        if (gitHubIntegration.test(gav)) {
            gav = gitHubIntegration.apply(gav);
        }
        var matcher = GAV_PATTERN.matcher(gav);
        if (!matcher.matches()) {
            System.err.println(String.format("GAV \"%s\" was not parseable", gav));
            return 1;
        }
        var groupId = matcher.group("group");
        var artifactId = matcher.group("artifact");
        var version = matcher.group("version");
        boolean exactMatch = !(version == null || "null".equals(version));
        if (exactMatch) {
            System.out.println(
                    String.format(
                            "Searching %s for version \"%s\" of %s from %s",
                            repoRoot, version, artifactId, groupId));
        } else {
            System.out.println(
                    String.format(
                            "Searching %s for available versions of %s from %s",
                            repoRoot, artifactId, groupId));
        }

        var url =
                String.format(
                        "%s/%s/%s/maven-metadata.xml",
                        repoRoot, groupId.replaceAll("\\.", "/"), artifactId);
        if (verbose) {
            // TODO do this without opening the URL stream twice
            System.out.println(String.format("Opening %s ...", url));
            try (var stream = new BufferedInputStream(new URL(url).openStream())) {
                System.out.println(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        var versioning = Versioning.from(url);

        if (exactMatch) {
            var versionMatch = versioning.contains(version);
            if (versionMatch.isPresent()) {
                System.out.println(
                        String.format(
                                "%s:%s:%s is available as %s in %s",
                                groupId, artifactId, version, versionMatch.get(), repoRoot));
            } else {
                System.err.println(
                        String.format(
                                "%s:%s:%s is NOT available in %s",
                                groupId, artifactId, version, repoRoot));
                System.err.println("available:");
                versioning.versions().stream()
                        .limit(count < 0 ? Integer.MAX_VALUE : count)
                        .map(v -> "\t" + v)
                        .forEach(System.err::println);
                return 2;
            }
        } else {
            System.out.println(String.format("latest: %s%n", versioning.latest()));
            System.out.println(String.format("release: %s%n", versioning.release()));
            System.out.println("available:");
            versioning.versions().stream()
                    .limit(count < 0 ? Integer.MAX_VALUE : count)
                    .map(v -> "\t" + v)
                    .forEach(System.out::println);
        }

        return 0;
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
