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

import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Properties;

public class ServerConnectionParametersProvider implements CommandLineArgumentProvider {
    private final Provider<Directory> settingsDirectory;

    public ServerConnectionParametersProvider(Provider<Directory> settingsDirectory) {
        this.settingsDirectory = settingsDirectory;
    }

    @Internal
    public Provider<Directory> getSettingsDirectory() {
        return settingsDirectory;
    }

    // Use of "input" here is not a mistake: we don't care about
    // the actual contents of the directory since it's just parameters
    // which will be used IF the tests are executed.
    @Input
    public Provider<String> getSettingsDirectoryString() {
        return settingsDirectory.map(directory -> directory.getAsFile().getName());
    }

    @Override
    public Iterable<String> asArguments() {
        Properties props = new Properties();
        File serverConfig = new File(settingsDirectory.get().getAsFile(), "test-resources.properties");
        if (serverConfig.exists()) {
            try (InputStream in = new FileInputStream(serverConfig)) {
                props.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return props.keySet()
                    .stream()
                    .map(key -> "-Dmicronaut.test.resources." + key + "=" + props.getProperty(key.toString()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
