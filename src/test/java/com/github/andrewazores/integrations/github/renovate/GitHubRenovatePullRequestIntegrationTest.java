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
package com.github.andrewazores.integrations.github.renovate;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.scripting.CliSupport;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubRenovatePullRequestIntegrationTest {

    @Mock CliSupport cli;
    GitHubRenovatePullRequestIntegration ghPr;

    @BeforeEach
    void setup() {
        this.ghPr = new GitHubRenovatePullRequestIntegration();
        this.ghPr.cli = cli;
    }

    @ParameterizedTest
    @CsvSource({
        "fix(deps): update dependency commons-validator:commons-validator to v1.10.1,"
                + "commons-validator, commons-validator, 1.10.1",
        "fix(deps): update dependency commons-io:commons-io to v2.17.0,"
                + "commons-io, commons-io, 2.17.0",
        "chore(deps): update dependency org.apache.commons:commons-text to v1.12.0,"
                + "org.apache.commons, commons-text, 1.12.0",
        "fix(deps): update io.netty.version to v4.1.111.Final,,,"
    })
    void testPrTitleAcceptance(String title, String group, String artifact, String version)
            throws Exception {
        var url = URI.create("http://example.com").toURL();
        var sr = new CliSupport.ScriptResult(0, List.of(title), List.of());
        Mockito.when(cli.script(Mockito.any(String[].class))).thenReturn(sr);
        if (group == null) {
            Assertions.assertThrows(RuntimeException.class, () -> ghPr.apply(url));
        } else {
            MatcherAssert.assertThat(
                    ghPr.apply(url),
                    Matchers.equalTo(List.of(new GroupArtifactVersion(group, artifact, version))));
        }
    }

    @Test
    void testPrTitleFromExampleFile() throws Exception {
        var url = URI.create("http://example.com").toURL();
        var title =
                Files.readString(
                        Paths.get(
                                getClass()
                                        .getClassLoader()
                                        .getResource("renovate-pr-title.txt")
                                        .toURI()));
        var sr = new CliSupport.ScriptResult(0, List.of(title.strip()), List.of());
        Mockito.when(cli.script(Mockito.any(String[].class))).thenReturn(sr);
        MatcherAssert.assertThat(
                ghPr.apply(url),
                Matchers.equalTo(
                        List.of(
                                new GroupArtifactVersion(
                                        "commons-validator", "commons-validator", "1.10.1"))));
    }

    @Test
    void testPrBodyFromExampleFile() throws Exception {
        var url = URI.create("http://example.com").toURL();
        var body =
                Files.readString(
                        Paths.get(
                                getClass()
                                        .getClassLoader()
                                        .getResource("renovate-pr-body.txt")
                                        .toURI()));
        var sr1 = new CliSupport.ScriptResult(0, List.of(""), List.of());
        var sr2 = new CliSupport.ScriptResult(0, List.of(body), List.of());
        Mockito.when(cli.script(Mockito.any(String[].class))).thenReturn(sr1).thenReturn(sr2);
        MatcherAssert.assertThat(
                ghPr.apply(url),
                Matchers.equalTo(
                        List.of(
                                new GroupArtifactVersion(
                                        "commons-validator", "commons-validator", "1.10.1"))));
    }

    @ParameterizedTest
    @CsvSource({
        "| [commons-validator:commons-validator](https://commons.apache.org/proper/commons-validator/)"
            + " ([source](https://redirect.github.com/apache/maven-apache-parent)) | `1.9.0` →"
            + " `1.10.1` |"
            + " ![age](https://developer.mend.io/api/mc/badges/age/maven/commons-validator:commons-validator/1.10.1?slim=true)"
            + " | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/commons-validator:commons-validator/1.9.0/1.10.1?slim=true)"
            + " |,commons-validator, commons-validator, 1.10.1",
        "| [io.netty:netty-bom](https://netty.io/) | `4.1.108.Final` → `4.1.111.Final` |"
            + " ![age](https://developer.mend.io/api/mc/badges/age/maven/io.netty:netty-bom/4.1.111.Final?slim=true)"
            + " | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/io.netty:netty-bom/4.1.108.Final/4.1.111.Final?slim=true)"
            + " |,io.netty, netty-bom, 4.1.111.Final"
    })
    void testPrBodyAcceptance(String body, String group, String artifact, String version)
            throws Exception {
        var url = URI.create("http://example.com").toURL();
        var sr1 = new CliSupport.ScriptResult(0, List.of(""), List.of());
        var sr2 = new CliSupport.ScriptResult(0, List.of(body), List.of());
        Mockito.when(cli.script(Mockito.any(String[].class))).thenReturn(sr1).thenReturn(sr2);
        if (group == null) {
            MatcherAssert.assertThat(ghPr.apply(url), Matchers.equalTo(List.of()));
        } else {
            MatcherAssert.assertThat(
                    ghPr.apply(url),
                    Matchers.equalTo(List.of(new GroupArtifactVersion(group, artifact, version))));
        }
    }
}
