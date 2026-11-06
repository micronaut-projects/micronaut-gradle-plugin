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
package io.micronaut.gradle.docker;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

public interface DockerExtension {
    Property<Boolean> getUseCopyLink();

    /**
     * Whether the JVM images write their dependency JARs and runner JARs with every entry STORED, so that the JVM
     * does not inflate the classes it reads from them. It makes starts without a JDK AOT cache faster, and the
     * unpacked image bigger. A repacked JAR no longer has the checksum of the published artifact, which some
     * vulnerability scanners rely on. Signed JARs are copied unchanged. Defaults to {@code false}.
     *
     * @return whether the dependency JARs are stored
     * @since 5.1.0
     */
    Property<Boolean> getStoredDependencies();

    /**
     * Whether the {@code main} JVM image of the {@code DEFAULT} build strategy packages its resources as a JAR in
     * the {@code resources} layer, instead of a directory. A directory first on the class path slows down every
     * class lookup of a start. Files copied or mounted into {@code /home/app/resources} are then no longer on
     * the class path. The JDK AOT cache needs the JAR. Defaults to {@code true}.
     *
     * @return whether the resources are packaged as a JAR
     * @since 5.1.0
     */
    Property<Boolean> getResourcesJar();

    /**
     * The JDK AOT cache that the generated JVM Docker images can train.
     *
     * @return the JDK AOT cache options
     * @since 5.1.0
     */
    @Nested
    JdkAotCacheOptions getJdkAotCache();

    /**
     * Configures the JDK AOT cache that the generated JVM Docker images can train.
     *
     * @param action the configuration action
     * @since 5.1.0
     */
    default void jdkAotCache(Action<? super JdkAotCacheOptions> action) {
        action.execute(getJdkAotCache());
    }
}
