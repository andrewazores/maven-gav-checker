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
package com.github.andrewazores.integrations.github.dependabot;

import java.net.URI;
import java.util.List;

import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.scripting.CliSupport;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubDependabotPullRequestIntegrationTest {

    @Mock CliSupport cli;
    GitHubDependabotPullRequestIntegration ghPr;

    @BeforeEach
    void setup() {
        this.ghPr = new GitHubDependabotPullRequestIntegration();
        this.ghPr.cli = cli;
    }

    @ParameterizedTest
    @CsvSource({
        "build(deps): bump commons-io:commons-io from 2.16.1 to 2.17.0,"
                + "commons-io, commons-io, 2.17.0",
        "build(deps-dev): bump org.apache.commons:commons-text from 1.11.0 to 1.12.0,"
                + " org.apache.commons, commons-text, 1.12.0",
        "build(deps): bump io.netty.version from 4.1.108.Final to 4.1.111.Final,,,"
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

    @ParameterizedTest
    @CsvSource({
        "Updates `io.netty:netty-bom` from 4.1.108.Final to 4.1.111.Final,"
                + "io.netty, netty-bom, 4.1.111.Final"
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
