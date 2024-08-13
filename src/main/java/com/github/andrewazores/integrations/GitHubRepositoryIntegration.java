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
package com.github.andrewazores.integrations;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.scripting.CliSupport;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
class GitHubRepositoryIntegration implements SourceIntegration {
    private static final Pattern GH_REPO_PATTERN =
            Pattern.compile(
                    "^https?://(?:www.)?github.com/(?<owner>[\\w.-]+)/(?<repo>[\\w.-]+)/?$",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern DEP_PATTERN =
            Pattern.compile(
                    "^[\\s]*(?<group>[a-z0-9._-]+):(?<artifact>[a-z0-9._-]+):(?<packaging>[a-z0-9._-]+):(?<version>[a-z0-9._-]+).*",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Inject CliSupport cli;

    @ConfigProperty(name = "maven-gav-checker.transitive-deps")
    boolean enableTransitiveDeps;

    @ConfigProperty(name = "maven-gav-checker.include-scope")
    String includeScope;

    @ConfigProperty(name = "maven-gav-checker.include-parent-pom")
    boolean includeParentPom;

    @Override
    public boolean test(URL url) {
        Log.debugv("Testing {0} on {1}", GH_REPO_PATTERN.pattern(), url.toString());
        var m = GH_REPO_PATTERN.matcher(url.toString());
        return m.matches();
    }

    @Override
    public List<GroupArtifactVersion> apply(URL url) throws IOException, InterruptedException {
        Log.debugv("Processing GitHub repository: {0}", url);
        var m = GH_REPO_PATTERN.matcher(url.toString());
        if (!m.matches()) throw new IllegalStateException();
        var owner = m.group("owner");
        var repo = m.group("repo");
        var repoId = String.format("%s/%s", owner, repo);
        var checkoutRef = getDefaultBranchRef(repoId);
        var pomUrl = getPomUrl(repoId, checkoutRef);
        var workDir = Files.createTempDirectory(getClass().getSimpleName());
        var pom = workDir.resolve("pom.xml");
        var depsFile = workDir.resolve("deps.txt");

        try (BufferedInputStream in = new BufferedInputStream(pomUrl.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(pom.toFile())) {
            var dataBuffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }

            if (Log.isDebugEnabled()) {
                Log.debug(Files.readString(pom));
            }

            cli.script(
                            "mvn",
                            "-B",
                            "-q",
                            "-Dsilent",
                            String.format("-DincludeScope=%s", includeScope),
                            String.format("-DexcludeTransitive=%b", !enableTransitiveDeps),
                            String.format("-DincludeParents=%b", includeParentPom),
                            "-Dmdep.outputScope=false",
                            String.format("-DoutputFile=%s", depsFile.toAbsolutePath().toString()),
                            String.format("--file=%s", pom.toAbsolutePath().toString()),
                            "dependency:list")
                    .assertOk();
            return Files.readAllLines(depsFile).stream()
                    .peek(l -> Log.tracev("dependency: {0}", l))
                    .filter(s -> DEP_PATTERN.matcher(s).matches())
                    .map(
                            s -> {
                                var m2 = DEP_PATTERN.matcher(s);
                                if (!m2.matches()) throw new IllegalStateException();
                                return new GroupArtifactVersion(
                                        m2.group("group"),
                                        m2.group("artifact"),
                                        m2.group("version"));
                            })
                    .toList();
        } finally {
            Files.deleteIfExists(pom);
            Files.deleteIfExists(depsFile);
        }
    }

    private String getDefaultBranchRef(String repo) throws IOException, InterruptedException {
        var proc =
                cli.script(
                                "gh",
                                "repo",
                                "view",
                                "--json=defaultBranchRef",
                                "--jq=.defaultBranchRef.name",
                                repo)
                        .assertOk();
        return proc.out().get(0);
    }

    private URL getPomUrl(String repo, String checkoutRef) {
        try {
            return new URL(
                    String.format(
                            "https://raw.githubusercontent.com/%s/%s/pom.xml", repo, checkoutRef));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
