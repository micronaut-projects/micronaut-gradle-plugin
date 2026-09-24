/*
 * Copyright 2003-2026 the original author or authors.
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
package io.micronaut.gradle.docker;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * Options of the JDK AOT cache (JEP 483, 514 and 515, JDK 25 or later) that the generated JVM Docker
 * images can train while they are built. When it is enabled, the Dockerfile runs the application once
 * with the image's own {@code java} and {@code -XX:AOTCacheOutput}, and the {@code ENTRYPOINT} starts it
 * with {@code -XX:AOTCache}.
 *
 * <p>This is not Micronaut AOT, which is configured with {@code micronaut { aot { } }}.</p>
 *
 * @since 5.0.3
 */
public interface JdkAotCacheOptions {

    /**
     * Whether the JVM Docker images train a JDK AOT cache. Defaults to {@code false}.
     * It applies to the Dockerfiles generated for the default build strategy only,
     * and requires a {@code jdkVersion} of 25 or later.
     *
     * @return the enabled property
     */
    @Input
    Property<Boolean> getEnabled();

    /**
     * The paths that the training run sends a GET request to, in order, once the application is ready.
     * Each path starts with {@code /} and must be answered with a status below 400. It may carry a
     * query string, but no whitespace and no comma. Defaults to no path, so the training run only
     * starts and stops the application.
     *
     * @return the training paths
     */
    @Input
    ListProperty<String> getTrainingPaths();

    /**
     * The longest time, in seconds, that the training run waits for the application to answer HTTP
     * requests, or to exit once it has been asked to stop. Defaults to 120.
     *
     * @return the training timeout, in seconds
     */
    @Input
    Property<Integer> getTrainingTimeout();

    /**
     * Whether the cache is created with {@code -XX:+AOTCompatibleOopCompression} when the base image's
     * JDK has that diagnostic flag (JDK 27 or later). Without it, a cache can fail to load depending on
     * where the heap is placed. Defaults to {@code true}.
     *
     * @return the compatible oop compression property
     */
    @Input
    Property<Boolean> getCompatibleOopCompression();

    /**
     * The number of strict launches ({@code -XX:AOTMode=on ... -version}) that check the cache once it
     * is written. A failing launch fails the image build. Defaults to 0.
     *
     * @return the number of strict probes
     */
    @Input
    Property<Integer> getStrictProbes();
}
