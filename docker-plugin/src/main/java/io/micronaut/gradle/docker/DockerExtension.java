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
     * The JDK AOT cache that the generated JVM Docker images can train.
     *
     * @return the JDK AOT cache options
     * @since 5.0.3
     */
    @Nested
    JdkAotCacheOptions getJdkAotCache();

    /**
     * Configures the JDK AOT cache that the generated JVM Docker images can train.
     *
     * @param action the configuration action
     * @since 5.0.3
     */
    default void jdkAotCache(Action<? super JdkAotCacheOptions> action) {
        action.execute(getJdkAotCache());
    }
}
