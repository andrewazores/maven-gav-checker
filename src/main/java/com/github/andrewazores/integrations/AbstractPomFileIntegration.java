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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.scripting.CliSupport;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public abstract class AbstractPomFileIntegration implements SourceIntegration {
    private static final Pattern DEP_PATTERN =
            Pattern.compile(
                    "^[\\s]*(?<group>[a-z0-9._-]+):(?<artifact>[a-z0-9._-]+):(?<packaging>[a-z0-9._-]+):(?<version>[a-z0-9._-]+).*",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Inject protected CliSupport cli;

    @ConfigProperty(name = "maven-gav-checker.transitive-deps")
    protected boolean enableTransitiveDeps;

    @ConfigProperty(name = "maven-gav-checker.include-scope")
    protected String includeScope;

    @ConfigProperty(name = "maven-gav-checker.include-parent-pom")
    protected boolean includeParentPom;

    @ConfigProperty(name = "maven-gav-checker.pom-url.supported-protocols")
    protected List<String> supportedProtocols;

    @Override
    public boolean test(URL url) {
        return supportedProtocols.contains(url.getProtocol()) && url.getPath().endsWith(".xml");
    }

    protected List<GroupArtifactVersion> process(Path pom)
            throws IOException, InterruptedException {
        Log.debugv("Processing XML file: {0}", pom);
        var workDir = Files.createTempDirectory(getClass().getSimpleName());
        var depsFile = workDir.resolve("deps.txt");

        try {
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
                                assert m2.matches();
                                return new GroupArtifactVersion(
                                        m2.group("group"),
                                        m2.group("artifact"),
                                        m2.group("version"));
                            })
                    .toList();
        } finally {
            Files.deleteIfExists(depsFile);
        }
    }
}
