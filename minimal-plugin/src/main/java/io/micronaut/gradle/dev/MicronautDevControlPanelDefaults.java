/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.dev;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Writes the defaults of Micronaut Control Panel in development mode as a classpath resource,
 * {@code micronaut-control-panel/micronaut-control-panel.properties}, which Control Panel reads as its property
 * source of the lowest precedence: the application's configuration files, its environment variables and the system
 * properties of the JVM all win over them.
 *
 * @since 5.0.3
 */
public abstract class MicronautDevControlPanelDefaults extends DefaultTask {

    /**
     * @return the properties, none to write no resource
     */
    @Input
    public abstract MapProperty<String, String> getProperties();

    /**
     * @return the classpath directory the resource is written to
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    void write() {
        Path resource = getOutputDirectory().get().getAsFile().toPath().resolve(MicronautDevControlPanel.DEFAULTS_RESOURCE);
        Properties properties = new Properties();
        properties.putAll(getProperties().get());
        try {
            if (properties.isEmpty()) {
                // Control Panel is off: the directory stays on the classpath, empty
                Files.deleteIfExists(resource);
                return;
            }
            Files.createDirectories(resource.getParent());
            try (OutputStream out = Files.newOutputStream(resource)) {
                properties.store(out, "Defaults of Micronaut Control Panel in development mode, written by the Micronaut Gradle plugin");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + resource, e);
        }
    }
}
