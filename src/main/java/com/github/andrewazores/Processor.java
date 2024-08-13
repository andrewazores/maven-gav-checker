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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.ParserConfigurationException;

import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.model.MavenVersioning;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.xml.sax.SAXException;

@ApplicationScoped
class Processor {

    public AtomicInteger execute(Collection<GroupArtifactVersion> gavs, String repoRoot, int count)
            throws InterruptedException {
        final var out = new AtomicInteger();
        final var latch = new CountDownLatch(gavs.size());
        final Map<GroupArtifactVersion, ProcessResult> results = new ConcurrentHashMap<>();

        gavs.forEach(
                gav -> {
                    ForkJoinPool.commonPool()
                            .submit(
                                    () -> {
                                        try {
                                            process(latch, repoRoot, gav, count, results);
                                        } catch (Exception e) {
                                            Log.error(e);
                                        } finally {
                                            latch.countDown();
                                        }
                                    });
                });

        latch.await();

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
                                                            .limit(
                                                                    count < 0
                                                                            ? Integer.MAX_VALUE
                                                                            : count)
                                                            .map(v -> "\t" + v)
                                                            .toList()));
                                    out.incrementAndGet();
                                }
                            } else {
                                Log.infov(
                                        "\nlatest:\t\t{0}\nrelease:\t{1}\navailable:\n{2}",
                                        entry.getValue().versioning().latest(),
                                        entry.getValue().versioning().release(),
                                        String.join(
                                                "\n",
                                                entry.getValue().versioning().versions().stream()
                                                        .limit(
                                                                count < 0
                                                                        ? Integer.MAX_VALUE
                                                                        : count)
                                                        .map(v -> "\t\t" + v)
                                                        .toList()));
                            }
                        });
        return out;
    }

    private void process(
            CountDownLatch latch,
            String repoRoot,
            GroupArtifactVersion gav,
            int count,
            Map<GroupArtifactVersion, ProcessResult> results)
            throws IOException, ParserConfigurationException, SAXException {
        boolean exactMatch = !(gav.version() == null || "null".equals(gav.version()));
        if (exactMatch) {
            Log.debugv(
                    "Searching {0} for version {1} of {2} from {3}",
                    repoRoot, gav.version(), gav.artifactId(), gav.groupId());
        } else {
            Log.debugv(
                    "Searching {0} for available versions of {1} from {2}",
                    repoRoot, gav.artifactId(), gav.groupId());
        }

        var url =
                String.format(
                        "%s/%s/%s/maven-metadata.xml",
                        repoRoot, gav.groupId().replaceAll("\\.", "/"), gav.artifactId());
        var versioning = MavenVersioning.from(url);

        if (exactMatch) {
            versioning
                    .bestMatch(gav)
                    .ifPresentOrElse(
                            match ->
                                    results.put(
                                            gav,
                                            new ProcessResult(
                                                    true,
                                                    new MavenVersioning(
                                                            match,
                                                            match,
                                                            List.of(match)))),
                            () -> results.put(gav, new ProcessResult(false, versioning)));
        } else {
            results.put(gav, new ProcessResult(!versioning.versions().isEmpty(), versioning));
        }
    }
}
