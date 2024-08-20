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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.github.andrewazores.integrations.SourceIntegration;
import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.scripting.CliSupport;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class GitHubDependabotPullRequestIntegration implements SourceIntegration {
    private static final Pattern GH_PR_TITLE_PATTERN =
            Pattern.compile(
                    "^build\\(deps\\): bump (?<group>[a-z0-9._-]+):(?<artifact>[a-z0-9._-]+) from"
                            + " (?:[a-z0-9._-]+) to (?<version>[a-z0-9._-]+)$",
                    Pattern.MULTILINE);
    private static final Pattern GH_PR_BODY_PATTERN =
            Pattern.compile(
                    "^Updates `(?<group>[^:]+):(?<artifact>.+)` from (?<from>.+) to (?<to>.+)$",
                    Pattern.MULTILINE);

    @Inject protected CliSupport cli;

    @Override
    public boolean test(URL url) {
        try {
            return ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol()))
                    && "github.com".equals(url.getHost())
                    && url.getPath().matches("/[\\w._-]+/[\\w._-]+/pull/[\\d]+/?");
        } catch (Exception e) {
            Log.trace(e);
            return false;
        }
    }

    @Override
    public List<GroupArtifactVersion> apply(URL url) throws IOException, InterruptedException {
        return matchesTitle(url)
                .or(
                        () -> {
                            try {
                                return matchesBody(url);
                            } catch (IOException | InterruptedException e) {
                                Log.error(e);
                                return Optional.empty();
                            }
                        })
                .orElseThrow(
                        () ->
                                new RuntimeException(
                                        String.format(
                                                "GitHub PR URL \"%s\" was not understandable. Is"
                                                        + " this a Dependabot Pull Request?",
                                                url)));
    }

    private Optional<List<GroupArtifactVersion>> matchesTitle(URL url)
            throws IOException, InterruptedException {
        cli.testCommand("gh");
        var proc =
                cli.script("gh", "pr", "view", url.toString(), "--json", "title", "--jq", ".title");
        Log.trace(proc.out().toString());
        proc.assertOk();
        var matcher = GH_PR_TITLE_PATTERN.matcher(proc.out().get(0));
        if (!matcher.matches()) {
            Log.debugv(
                    "GitHub PR URL {0} was not understandable. Got title: {1}. Is"
                            + " this a Dependabot Pull Request? Does the title contain a single"
                            + " GroupId:ArtifactId or a Maven property for upgrading a"
                            + " dependency group?",
                    url, proc.out().get(0));
            return Optional.empty();
        }
        var group = matcher.group("group");
        var artifact = matcher.group("artifact");
        var version = matcher.group("version");
        Log.debugv(
                "Interpreted GitHub PR title \"{0}\" as request for {1}:{2}:{3}",
                proc.out().get(0), group, artifact, version);
        return Optional.of(List.of(new GroupArtifactVersion(group, artifact, version)));
    }

    private Optional<List<GroupArtifactVersion>> matchesBody(URL url)
            throws IOException, InterruptedException {
        cli.testCommand("gh");
        var proc =
                cli.script("gh", "pr", "view", url.toString(), "--json", "body", "--jq", ".body");
        var body = String.join("\n", proc.out());
        Log.trace(body);
        proc.assertOk();
        var matcher = GH_PR_BODY_PATTERN.matcher(body);
        var result = new ArrayList<GroupArtifactVersion>();
        boolean anyFound = false;
        while (true) {
            boolean found = matcher.find();
            anyFound |= found;
            if (!found) break;
            var group = matcher.group("group");
            var artifact = matcher.group("artifact");
            var version = matcher.group("to");
            var gav = new GroupArtifactVersion(group, artifact, version);
            Log.tracev("Found {0}", gav);
            result.add(gav);
        }
        if (!anyFound) {
            Log.debugv(
                    "GitHub PR URL {0} was not understandable. Got body: {1}. Is this a Dependabot"
                            + " Pull Request? Does the body contain a list of 'Updates"
                            + " `groupId:artifactId` from $from to $version' strings?",
                    url, body);
            return Optional.empty();
        }
        return Optional.of(result);
    }
}
