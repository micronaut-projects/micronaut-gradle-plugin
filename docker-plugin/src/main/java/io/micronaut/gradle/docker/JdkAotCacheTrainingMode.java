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

/**
 * How the image build trains the JDK AOT cache.
 *
 * @see JdkAotCacheOptions#getTrainingMode()
 * @since 5.1.0
 */
public enum JdkAotCacheTrainingMode {

    /**
     * The training run loads the bean definitions of the application and the classes they name, without
     * starting the application, so it needs none of the services the application uses, such as a database.
     * It needs a Micronaut version that has this mode.
     */
    LOAD,

    /**
     * The training run starts the application, requests the
     * {@link JdkAotCacheOptions#getTrainingPaths() training paths} and stops it. The cache is more complete,
     * but the application must be able to start while the image is built.
     */
    START
}
