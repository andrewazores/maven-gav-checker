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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public record MavenVersioning(String latest, String release, List<String> versions) {
    public MavenVersioning(String latest, String release, List<String> versions) {
        this.latest = latest;
        this.release = release;
        this.versions = Collections.unmodifiableList(new ArrayList<>(versions));
    }

    public Optional<String> contains(String version) {
        return versions.stream().filter(v -> versionCompare(version, v)).findFirst();
    }

    public static MavenVersioning from(String uri)
            throws IOException, ParserConfigurationException, SAXException {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        var documentBuilder = factory.newDocumentBuilder();
        var xmlDoc = documentBuilder.parse(uri);

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
