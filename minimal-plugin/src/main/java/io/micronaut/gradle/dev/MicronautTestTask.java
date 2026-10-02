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

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.CommandLineArgumentProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs {@code io.micronaut.dev.MicronautDevMain} in test mode, {@code ./gradlew mnTest}: the tests a change
 * affects run on a new generation, in the same JVM, until {@code q} is pressed, and the task fails when the last
 * run did. The options and the build's {@code micronaut.dev.*} system properties reach the launcher as system
 * properties, which override the entries of the manifest.
 *
 * @since 5.0.3
 */
public abstract class MicronautTestTask extends JavaExec {

    /**
     * Passes the options and the overrides to the launcher.
     */
    public MicronautTestTask() {
        getJvmArgumentProviders().add(new SystemProperties(getTestFilter(), getOnce(), getOverrides()));
    }

    /**
     * The tests to run, as Gradle's {@code --tests}: a class name or a glob of one, or {@code Class.method}.
     *
     * @return the patterns
     */
    @Input
    @Option(option = "tests", description = "Runs only the tests that match: a class, a glob of one, or Class.method. May be repeated.")
    public abstract ListProperty<String> getTestFilter();

    /**
     * Whether the tests run once and the launcher exits with their status, rather than watching for changes.
     *
     * @return the flag
     */
    @Input
    @Optional
    @Option(option = "once", description = "Runs the tests once and exits with their status.")
    public abstract Property<Boolean> getOnce();

    /**
     * The {@code micronaut.dev.*} system properties of the build, passed on to the launcher.
     *
     * @return the properties
     */
    @Input
    public abstract MapProperty<String, String> getOverrides();

    /**
     * The options and overrides as system properties of the launcher.
     */
    private record SystemProperties(ListProperty<String> filter, Property<Boolean> once, MapProperty<String, String> overrides) implements CommandLineArgumentProvider {

        @Override
        public Iterable<String> asArguments() {
            Map<String, String> properties = new LinkedHashMap<>(overrides.get());
            if (!filter.get().isEmpty()) {
                properties.put(MicronautDevManifest.PREFIX + "test.filter", String.join(",", filter.get()));
            }
            if (once.isPresent()) {
                properties.put(MicronautDevManifest.PREFIX + "test.once", String.valueOf(once.get()));
            }
            List<String> arguments = new ArrayList<>(properties.size());
            properties.forEach((key, value) -> arguments.add("-D" + key + "=" + value));
            return arguments;
        }
    }
}
