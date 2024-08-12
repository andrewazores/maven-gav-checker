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
import java.util.regex.Pattern;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class GitHubIntegration implements SourceIntegration {
    private static final Pattern GH_PR_TITLE_PATTERN =
            Pattern.compile(
                    "^build\\(deps\\): bump (?<ga>[a-z0-9._-]+:[a-z0-9._-]+) from (?:[a-z0-9._-]+)"
                            + " to (?<version>[a-z0-9._-]+)$",
                    Pattern.MULTILINE);

    @Inject CliSupport cli;

    @Override
    public boolean test(String url) {
        return url.startsWith("https://github.com") || url.startsWith("http://github.com");
    }

    @Override
    public String apply(String url) throws IOException, InterruptedException {
        cli.testCommand("gh");
        var proc = cli.script("gh", "pr", "view", url, "--json", "title", "--jq", ".title");
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
        var ga = matcher.group("ga");
        var version = matcher.group("version");
        Log.debugv(
                "Interpreted GitHub PR title \"{0}\" as request for {1}:{2}",
                proc.out().get(0), ga, version);
        return String.format("%s:%s", ga, version);
    }
}
