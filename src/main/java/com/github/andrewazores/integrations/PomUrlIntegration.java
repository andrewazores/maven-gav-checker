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

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.andrewazores.model.GroupArtifactVersion;
import com.github.andrewazores.scripting.CliSupport;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PomUrlIntegration extends AbstractPomFileIntegration {

    @Inject protected CliSupport cli;

    @ConfigProperty(name = "maven-gav-checker.pom-url.supported-protocols")
    protected List<String> supportedProtocols;

    @Override
    public boolean test(URL url) {
        return supportedProtocols.contains(url.getProtocol()) && url.getPath().endsWith(".xml");
    }

    @Override
    public List<GroupArtifactVersion> apply(URL url) throws IOException, InterruptedException {
        Log.debugv("Processing XML URL: {0}", url);

        if ("file".equals(url.getProtocol())) {
            return process(Path.of(url.getPath()));
        }

        var workDir = Files.createTempDirectory(getClass().getSimpleName());
        var pom = workDir.resolve("pom.xml");
        try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(pom.toFile())) {
            var dataBuffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }

            return process(pom);
        } finally {
            Files.deleteIfExists(pom);
        }
    }
}
