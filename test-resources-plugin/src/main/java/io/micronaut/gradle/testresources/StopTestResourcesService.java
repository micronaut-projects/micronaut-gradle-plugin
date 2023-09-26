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
package io.micronaut.gradle.testresources;

import io.micronaut.testresources.buildtools.ServerUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.Destroys;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;

/**
 * A task to explicitly stop the test resources server.
 */
public abstract class StopTestResourcesService extends DefaultTask {
    @Destroys
    public abstract DirectoryProperty getSettingsDirectory();

    @Inject
    protected abstract FileOperations getFileOperations();

    @Inject
    public StopTestResourcesService() {
        setGroup(MicronautTestResourcesPlugin.GROUP);
        setDescription("Stops the test resources server");
    }

    @TaskAction
    void stopServer() throws IOException {
        getLogger().lifecycle("Stopping test resources service");
        ServerUtils.stopServer(getSettingsDirectory().get().getAsFile().toPath());
        getFileOperations().delete(getSettingsDirectory());
    }
}
