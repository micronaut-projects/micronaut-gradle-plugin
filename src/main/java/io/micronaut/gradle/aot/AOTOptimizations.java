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
package io.micronaut.gradle.aot;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * This interface represents the set of optimizations
 * that this particular version of the Gradle plugin
 * knows about.
 */
public interface AOTOptimizations {

    /**
     * If set to true, logback.xml is replaced with Java configuration
     * @return the logback configuration
     */
    @Input
    Property<Boolean> getReplaceLogbackXml();

    /**
     * If set to true, the environment variables and system properties
     * read at application startup will be deemed immutable
     * @return the seal environment property
     */
    @Input
    Property<Boolean> getSealEnvironment();

    /**
     * If set to true, services loading will be optimized. In particular,
     * service definitions requirements will be evaluated ahead of time.
     * @return the service loading property
     */
    @Input
    Property<Boolean> getOptimizeServiceLoading();

    /**
     * If set to true, YAML files will be converted to Java configuration.
     * @return the yaml conversion property.
     */
    @Input
    Property<Boolean> getConvertYamlToJava();
}
