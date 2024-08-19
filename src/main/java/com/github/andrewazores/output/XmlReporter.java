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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github.andrewazores.ProcessResult;
import com.github.andrewazores.model.GroupArtifactVersion;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class XmlReporter implements OutputReporter {

    private final XmlMapper mapper = XmlMapper.builder().build();

    @Override
    public String formatSpecifier() {
        return "xml";
    }

    @Override
    public void accept(Map<GroupArtifactVersion, ProcessResult> results, String repoRoot) {
        try {
            Log.info(
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(
                                    Map.of("repository", repoRoot, "results", results)));
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
    }
}
