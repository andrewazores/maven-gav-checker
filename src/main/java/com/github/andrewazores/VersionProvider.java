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

import io.quarkus.arc.Unremovable;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine.IVersionProvider;

@Unremovable
@Singleton
class VersionProvider implements IVersionProvider {
    @ConfigProperty(name = "quarkus.application.name")
    String name;

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @Override
    public String[] getVersion() throws Exception {
        return new String[] {name, version};
    }
}
