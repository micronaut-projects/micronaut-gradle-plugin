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

import io.micronaut.testresources.buildtools.KnownModules;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the test resources plugin.
 */
public interface TestResourcesConfiguration extends KnownModules {

    /**
     * If set to false, test resources support will be disabled.
     * @return the enabled property
     */
    Property<Boolean> getEnabled();

    /**
     * The version of the Micronaut Test Resources library
     * to use. Defaults to the version embedded in the
     * Gradle plugin.
     *
     * @return the version of the Micronaut Test Resources library
     */
    Property<String> getVersion();

    /**
     * If set, then the test resources server will
     * be started using the specified port. If a server
     * already exists on the port, then the build will
     * reuse that server, effectively ignoring the
     * existing configuration. This can be used to share
     * a server between multiple independent builds.
     *
     * If an explicit port is set, then security is lowered
     * and a token is no longer requested. Any client from
     * the loopback address can access the server.
     *
     * @return the explicit port to use
     */
    Property<Integer> getExplicitPort();

    /**
     * If set to false, then the plugin will not try to infer
     * the test resources modules to include by inspecting
     * the project dependencies: instead the user will need
     * to add all modules explicitly.
     *
     * @return the infer classpath property
     */
    Property<Boolean> getInferClasspath();

    /**
     * Additional modules to include on the test resources
     * classpath. For example, if you add "kafka" to this
     * list, then the "micronaut-test-resources-kafka" module
     * will automatically be added to the test resources
     * classpath.
     */
    ListProperty<String> getAdditionalModules();

    /**
     * Configures the maximum amount of time to wait for
     * the server to start a test resource. Some containers
     * may take a long amount of time to start with slow internet
     * connections.
     * Defaults to 60.
     * @return the client timeout in seconds
     */
    Property<Integer> getClientTimeout();

    /**
     * When set to true, then the server will be shared between
     * independent builds (e.g. two different projects built in separate
     * checkout directories).
     *
     * @return the shared server property
     */
    Property<Boolean> getSharedServer();

    /**
     * Allows configuring a namespace for the shared test resources
     * server. This can be used in case it makes sense to have
     * different instances of shared services, for example when
     * independent builds sets share different services.
     * @return the namespace
     */
    Property<String> getSharedServerNamespace();
}
