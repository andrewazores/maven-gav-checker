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
package com.github.andrewazores.output;

import java.util.Map;

import com.github.andrewazores.ProcessResult;
import com.github.andrewazores.model.GroupArtifactVersion;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class HumanReporter implements OutputReporter {

    @Override
    public String formatSpecifier() {
        return "human";
    }

    @Override
    public void accept(Map<GroupArtifactVersion, ProcessResult> results, String repoRoot) {
        results.entrySet()
                .forEach(
                        entry -> {
                            var gav = entry.getKey();
                            boolean exactMatch =
                                    !(entry.getKey().version() == null
                                            || "null".equals(entry.getKey().version()));
                            if (exactMatch) {
                                if (entry.getValue().available()) {
                                    Log.infov(
                                            "{0}:{1}:{2} is available as {3} in {4}",
                                            gav.groupId(),
                                            gav.artifactId(),
                                            gav.version(),
                                            entry.getValue().versioning().versions().get(0),
                                            repoRoot);
                                } else {
                                    Log.errorv(
                                            "{0}:{1}:{2} is NOT available in {3}.\navailable:\n{4}",
                                            gav.groupId(),
                                            gav.artifactId(),
                                            gav.version(),
                                            repoRoot,
                                            String.join(
                                                    "\n",
                                                    entry
                                                            .getValue()
                                                            .versioning()
                                                            .versions()
                                                            .stream()
                                                            .map(v -> "\t" + v)
                                                            .toList()));
                                }
                            } else {
                                Log.infov(
                                        "\nlatest:\t\t{0}\nrelease:\t{1}\navailable:\n{2}",
                                        entry.getValue().versioning().latest(),
                                        entry.getValue().versioning().release(),
                                        String.join(
                                                "\n",
                                                entry.getValue().versioning().versions().stream()
                                                        .map(v -> "\t\t" + v)
                                                        .toList()));
                            }
                        });
    }
}
