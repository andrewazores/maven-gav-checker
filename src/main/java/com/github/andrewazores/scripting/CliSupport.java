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
package com.github.andrewazores.scripting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CliSupport {
    public void testCommand(String command) {
        try {
            if (!script("command", "-v", command).ok()) {
                throw new UnavailableCommandException(command);
            }
        } catch (IOException | InterruptedException e) {
            throw new UnavailableCommandException(command, e);
        }
    }

    public ScriptResult script(String... command) throws IOException, InterruptedException {
        Log.trace(String.join(" ", Arrays.asList(command)));
        var proc = new ProcessBuilder().command(command).start();
        var out = proc.inputReader().lines().toList();
        var err = proc.errorReader().lines().toList();
        int sc = proc.waitFor();
        return new ScriptResult(sc, out, err);
    }

    public static record ScriptResult(int statusCode, List<String> out, List<String> err) {
        public ScriptResult(int statusCode, List<String> out, List<String> err) {
            this.statusCode = statusCode;
            this.out = Collections.unmodifiableList(new ArrayList<>(out));
            this.err = Collections.unmodifiableList(new ArrayList<>(err));
        }

        public ScriptResult assertOk() {
            if (!ok()) {
                throw new IllegalStateException(
                        String.format(
                                "%nstdout:%n%s%nstderr:%n%s",
                                String.join("\n", out()), String.join("\n", err())));
            }
            return this;
        }

        public boolean ok() {
            return statusCode == 0;
        }
    }
}
