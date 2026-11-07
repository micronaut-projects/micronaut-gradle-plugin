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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.process.JavaForkOptions;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What {@code mnDev} takes from the {@code run} task, and {@code mnTest} from the {@code test} task: the JVM arguments,
 * the system properties, such as {@code micronaut.environments}, the environment variables the build adds or changes,
 * such as {@code MICRONAUT_ENVIRONMENTS}, and the heap sizes, as the build configured them. The launcher's JVM is the
 * application's, or the tests', so it runs as {@code run} or {@code test} would.
 *
 * <p>What the development task sets itself wins, and is not repeated. The JVM argument providers are not taken: the
 * plugins add them, to {@code run} for its own launch, and those development mode needs, such as the connection to
 * the test resources server, to the development tasks themselves. Nor are the program arguments, the working
 * directory and the debug options, which are the launcher's own.</p>
 *
 * @param jvmArgs the JVM arguments of the task taken from
 * @param systemProperties its system properties, a null value being a property without one
 * @param environment the environment variables it adds or changes
 * @param minHeapSize its minimum heap size, if set
 * @param maxHeapSize its maximum heap size, if set
 */
record InheritedForkOptions(List<String> jvmArgs,
                            Map<String, String> systemProperties,
                            Map<String, String> environment,
                            String minHeapSize,
                            String maxHeapSize) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The options a development task takes from another, read once the build is configured: when the configuration
     * cache stores the task, or when it runs.
     *
     * @param project the project
     * @param from the name of the task taken from, {@code run} or {@code test}
     * @param to the name of the development task
     * @return the options, none when there is no task to take from
     */
    static Provider<InheritedForkOptions> between(Project project, String from, String to) {
        TaskContainer tasks = project.getTasks();
        return project.provider(() -> {
            Task source = tasks.findByName(from);
            Task target = tasks.findByName(to);
            if (source instanceof JavaForkOptions sourceOptions && target instanceof JavaForkOptions targetOptions) {
                return of(sourceOptions, targetOptions);
            }
            return null;
        });
    }

    /**
     * @param source the options taken from
     * @param target the options of the development task, which win
     * @return what the development task takes
     */
    static InheritedForkOptions of(JavaForkOptions source, JavaForkOptions target) {
        List<String> targetJvmArgs = target.getJvmArgs() == null ? List.of() : target.getJvmArgs();
        List<String> jvmArgs = new ArrayList<>();
        if (source.getJvmArgs() != null) {
            for (String argument : source.getJvmArgs()) {
                if (!targetJvmArgs.contains(argument)) {
                    jvmArgs.add(argument);
                }
            }
        }
        Map<String, String> systemProperties = new LinkedHashMap<>();
        source.getSystemProperties().forEach((key, value) -> {
            if (!target.getSystemProperties().containsKey(key)) {
                systemProperties.put(key, value == null ? null : String.valueOf(value));
            }
        });
        // both start from the build's environment: what differs is what the build added or changed
        Map<String, String> environment = new LinkedHashMap<>();
        source.getEnvironment().forEach((key, value) -> {
            String text = value == null ? null : String.valueOf(value);
            Object current = target.getEnvironment().get(key);
            if (text != null && !Objects.equals(text, current == null ? null : String.valueOf(current))) {
                environment.put(key, text);
            }
        });
        return new InheritedForkOptions(jvmArgs, systemProperties, environment,
            target.getMinHeapSize() == null ? source.getMinHeapSize() : null,
            target.getMaxHeapSize() == null ? source.getMaxHeapSize() : null);
    }

    /**
     * Applies the options to the development task.
     *
     * @param target the options of the development task
     */
    void applyTo(JavaForkOptions target) {
        target.jvmArgs(jvmArgs);
        systemProperties.forEach(target::systemProperty);
        environment.forEach(target::environment);
        if (minHeapSize != null) {
            target.setMinHeapSize(minHeapSize);
        }
        if (maxHeapSize != null) {
            target.setMaxHeapSize(maxHeapSize);
        }
    }
}
