/*
 * Copyright 2003-2022 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

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
     * read at application startup will be deemed immutable.
     * @return the cache environment property
     */
    @Input
    Property<Boolean> getCacheEnvironment();

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

    /**
     * If set to true, the AOT compiler will optimize classloading.
     */
    @Input
    Property<Boolean> getOptimizeClassLoading();

    /**
     * If set to true, the AOT will precompute at build time a number of
     * different types of information which would have been computed at
     * runtime instead.
     *
     * @return the precompute operation property
     */
    @Input
    Property<Boolean> getPrecomputeOperations();

    /**
     * If set to true and that the application context uses deduceEnvironment(true)
     * then the deduction will be done at build time instead of run time.
     *
     * @return the deduce environment property
     */
    @Input
    Property<Boolean> getDeduceEnvironment();

    /**
     * Sets the list of possible environment names. If set, then the list of environments
     * which will be considered for some AOT optimizations will use this list, instead
     * of using whatever is active when the dynamic AOT analysis is performed.
     *
     * @return the list of possible environments
     */
    @Input
    @Optional
    ListProperty<String> getPossibleEnvironments();

    /**
     * Sets the list of target environment names. If set, then the list of environments
     * is automatically configured before the AOT analysis starts. Note that it differs
     * from {@link #getPossibleEnvironments()} that this call will override whatever
     * the application context is configured to use.
     *
     * @return the list of target environments
     */
    @Input
    @Optional
    ListProperty<String> getTargetEnvironments();

    /**
     * An optional map of properties which will be merged with the configuration
     * to generate the final configuration file of Micronaut AOT.
     * @return the configuration properties
     */
    @Input
    @Optional
    MapProperty<String, String> getConfigurationProperties();

    /**
     * The target package for AOT generated classes.
     * For applications it defaults to the package of the main class.
     * @return the target package property
     */
    @Input
    Property<String> getTargetPackage();

    /**
     * Configures environment variables which will be injected
     * during AOT analysis. This can be useful whenever some
     * beans require environment variables to be present, but
     * that those are not available when the AOT analysis is
     * performed.
     *
     * @return the map of environment variables
     */
    @Input
    @Optional
    MapProperty<String, String> getEnvironmentVariables();

    /**
     * Returns the Netty optimizations
     * @return the netty optimizations
     */
    @Nested
    NettyOptimizations getNettyOptimizations();

    /**
     * Configures the Netty optimizations
     */
    default void netty(Action<? super NettyOptimizations> configuration) {
        configuration.execute(getNettyOptimizations());
    }

    /**
     * A short-hand notation to enable Netty optimizations
     * @return the netty optimizations property
     */
    @Internal
    default Property<Boolean> getOptimizeNetty() {
        return getNettyOptimizations().getEnabled();
    }
}
