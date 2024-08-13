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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.scripting.CliSupport;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class GitHubRepositoryIntegration extends PomUrlIntegration {
    private static final Pattern GH_REPO_PATTERN =
            Pattern.compile(
                    "^https?://(?:www.)?github.com/(?<owner>[\\w.-]+)/(?<repo>[\\w.-]+)/?$",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Inject CliSupport cli;

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
        assert m.matches();
        var owner = m.group("owner");
        var repo = m.group("repo");
        var repoId = String.format("%s/%s", owner, repo);
        var checkoutRef = getDefaultBranchRef(repoId);
        var pomUrl = getPomUrl(repoId, checkoutRef);
        return super.apply(pomUrl);
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
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
