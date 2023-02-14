/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.docker.tasks;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DockerResourceConfigDirectoryNamer {
    private final Map<String, Integer> counter = new HashMap<>();

    public String determineNameFor(File configDir) {
        String name = configDir.getName();
        File currentDir = configDir;
        for (int i = 0; i < 3; i++) {
            if (currentDir != null) {
                currentDir = currentDir.getParentFile();
            }
        }
        if (currentDir != null && "exploded".equals(currentDir.getName())) {
            // This directory likely comes from the GraalVM metadata repository plugin
            Path fullPath = configDir.toPath();
            Path relativePath = currentDir.toPath().relativize(fullPath);
            name = relativePath.toString();
        }
        Integer count = counter.computeIfAbsent(name, k -> 0);
        if (count == 0) {
            return name;
        } else {
            count++;
            counter.put(name, count);
            return name + "/" + count;
        }
    }
}
