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
import java.util.List;
import java.util.regex.Pattern;

import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.scripting.CliSupport;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class GitHubPullRequestIntegration implements SourceIntegration {
    private static final Pattern GH_PR_TITLE_PATTERN =
            Pattern.compile(
                    "^build\\(deps\\): bump (?<group>[a-z0-9._-]+):(?<artifact>[a-z0-9._-]+) from"
                            + " (?:[a-z0-9._-]+) to (?<version>[a-z0-9._-]+)$",
                    Pattern.MULTILINE);

    @Inject CliSupport cli;

    @Override
    public boolean test(URL url) {
        try {
            return ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol()))
                    && "github.com".equals(url.getHost())
                    && url.getPath().matches("/[\\w-_\\.]+/[\\w-_\\.]+/pull/[\\d]+/?");
        } catch (Exception e) {
            Log.trace(e);
            return false;
        }
    }

    @Override
    public List<GroupArtifactVersion> apply(URL url) throws IOException, InterruptedException {
        cli.testCommand("gh");
        var proc =
                cli.script("gh", "pr", "view", url.toString(), "--json", "title", "--jq", ".title");
        Log.trace(proc.out().toString());
        proc.assertOk();
        var matcher = GH_PR_TITLE_PATTERN.matcher(proc.out().get(0));
        if (!matcher.matches()) {
            throw new RuntimeException(
                    String.format(
                            "GitHub PR URL \"%s\" was not understandable. Got title: \"%s\". Is"
                                + " this a Dependabot Pull Request? Does the title contain a single"
                                + " GroupId:ArtifactId or a Maven property for upgrading a"
                                + " dependency group?",
                            url, proc.out().get(0)));
        }
        var group = matcher.group("group");
        var artifact = matcher.group("artifact");
        var version = matcher.group("version");
        Log.debugv(
                "Interpreted GitHub PR title \"{0}\" as request for {1}:{2}:{3}",
                proc.out().get(0), group, artifact, version);
        return List.of(new GroupArtifactVersion(group, artifact, version));
    }
}
