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
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public record Versioning(String latest, String release, List<String> versions) {
    public Optional<String> contains(String version) {
        return versions.stream().filter(v -> versionCompare(version, v)).findFirst();
    }

    public static Versioning from(String uri)
            throws IOException, ParserConfigurationException, SAXException {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        var documentBuilder = factory.newDocumentBuilder();
        var xmlDoc = documentBuilder.parse(uri);

        var root = xmlDoc.getDocumentElement();

        var versioning = getChild(root, "versioning").orElseThrow();
        var latest = getChild(versioning, "latest").orElseThrow().getTextContent();
        var release = getChild(versioning, "release").orElseThrow().getTextContent();
        var versions = getChild(versioning, "versions").orElseThrow();
        var versionList =
                new ArrayList<>(
                        getChildren(versions, "version").stream()
                                .map(Node::getTextContent)
                                .toList());
        Collections.reverse(versionList);

        return new Versioning(latest, release, Collections.unmodifiableList(versionList));
    }

    private static Optional<Node> getChild(Node parent, String childName) {
        NodeList list = parent.getChildNodes();
        int idx = 0;
        while (idx < list.getLength()) {
            var node = list.item(idx);
            var name = node.getNodeName();
            if (childName.equals(name)) {
                return Optional.of(node);
            }
            idx++;
        }
        return Optional.empty();
    }

    private static List<Node> getChildren(Node parent, String childName) {
        List<Node> out = new ArrayList<>();
        NodeList list = parent.getChildNodes();
        int idx = 0;
        while (idx < list.getLength()) {
            var node = list.item(idx);
            var name = node.getNodeName();
            if (childName.equals(name)) {
                out.add(node);
            }
            idx++;
        }
        return out;
    }

    private static boolean versionCompare(String request, String found) {
        return found.startsWith(String.format("%s-", request))
                || found.startsWith(String.format("%s.", request));
    }
}
