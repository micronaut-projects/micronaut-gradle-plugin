/*
 * Copyright 2003-2022 the original author or authors.
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
package io.micronaut.gradle.docker.model;

public enum LayerKind {
    PROJECT_LIBS("project_libs", "libs"),
    SNAPSHOT_LIBS("snapshot_libs", "libs"),
    LIBS("libs", "libs"),
    APP("app", "");

    private final String sourceDirName;
    private final String targetDirName;

    LayerKind(String sourceDirName, String targetDirName) {
        this.sourceDirName = sourceDirName;
        this.targetDirName = targetDirName;
    }

    public String targetDirName() {
        return targetDirName;
    }

    public String sourceDirName() {
        return sourceDirName;
    }
}
