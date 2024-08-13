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
package com.github.andrewazores.model;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.github.andrewazores.util.XmlParser;
import io.quarkus.logging.Log;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public record MavenVersioning(String latest, String release, List<String> versions) {
    public MavenVersioning(String latest, String release, List<String> versions) {
        this.latest = latest;
        this.release = release;
        this.versions = Collections.unmodifiableList(new ArrayList<>(versions));
    }

    public boolean contains(String version) {
        return versions.stream().anyMatch(v -> versionCompare(version, v));
    }

    public Optional<String> bestMatch(GroupArtifactVersion gav) {
        return versions.stream().filter(v -> versionCompare(gav.version(), v)).findFirst();
    }

    public static MavenVersioning from(String url)
            throws IOException, ParserConfigurationException, SAXException {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        var documentBuilder = factory.newDocumentBuilder();

        Log.debugv("Opening {0} ...", url);
        if (Log.isDebugEnabled()) {
            // TODO do this without opening the URL stream twice
            try (var stream = new BufferedInputStream(new URL(url).openStream())) {
                Log.debug(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        var xmlDoc = documentBuilder.parse(url);

        var root = xmlDoc.getDocumentElement();

        var versioning = XmlParser.getChild(root, "versioning").orElseThrow();
        var latest =
                XmlParser.getChild(versioning, "latest").map(Node::getTextContent).orElse("N/A");
        var release =
                XmlParser.getChild(versioning, "release").map(Node::getTextContent).orElse("N/A");
        var versions = XmlParser.getChild(versioning, "versions").orElseThrow();
        var versionList =
                new ArrayList<>(
                        XmlParser.getChildren(versions, "version").stream()
                                .map(Node::getTextContent)
                                .toList());
        Collections.reverse(versionList);

        return new MavenVersioning(latest, release, versionList);
    }

    private static boolean versionCompare(String request, String found) {
        return found.startsWith(String.format("%s-", request))
                || found.startsWith(String.format("%s.", request));
    }
}
