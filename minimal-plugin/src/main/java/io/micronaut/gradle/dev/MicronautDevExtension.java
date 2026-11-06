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

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * Configures development mode, {@code ./gradlew mnDev}: the reload strategy, how sources are
 * compiled, what survives a restart, and the LiveReload server; and test mode, {@code ./gradlew mnTest}:
 * which tests a change runs.
 *
 * <pre>
 * micronaut {
 *     dev {
 *         strategy = "auto"          // restart, reload or auto
 *         compile = "embedded"       // embedded or build-tool
 *         incremental = true
 *         retain("javax.sql.DataSource")
 *         liveReload {
 *             enabled = true
 *             port = 35729
 *         }
 *         test {
 *             selection = "affected" // affected or all
 *             initialRun = true
 *             reportPath = "/tests/" // where LiveReload serves the live report
 *         }
 *     }
 * }
 * </pre>
 *
 * @since 5.0.3
 */
public abstract class MicronautDevExtension {

    /**
     * The reload strategy: {@code restart}, {@code reload} or {@code auto}. Defaults to {@code auto}.
     *
     * @return the strategy
     */
    public abstract Property<String> getStrategy();

    /**
     * How sources are compiled after a change: {@code embedded}, in the development JVM, or
     * {@code build-tool}, by Gradle. Defaults to {@code embedded}.
     *
     * @return the compile mode
     */
    public abstract Property<String> getCompile();

    /**
     * Whether the embedded compilers compile incrementally. Defaults to true.
     *
     * @return the flag
     */
    public abstract Property<Boolean> getIncremental();

    /**
     * The types whose singletons are kept across a restart.
     *
     * @return the qualified type names
     */
    public abstract ListProperty<String> getRetain();

    /**
     * Adds types whose singletons are kept across a restart.
     *
     * @param types the qualified type names
     */
    public void retain(String... types) {
        getRetain().addAll(types);
    }

    /**
     * Extra options for the embedded Kotlin compiler, such as the options of a compiler plugin the
     * plugin cannot read from the compilation: {@code -P plugin:org.jetbrains.kotlin.noarg:annotation=...}.
     *
     * @return the options
     */
    public abstract ListProperty<String> getKotlinCompilerArgs();

    /**
     * Adds options for the embedded Kotlin compiler.
     *
     * @param args the options
     */
    public void kotlinCompilerArgs(String... args) {
        getKotlinCompilerArgs().addAll(args);
    }

    /**
     * The LiveReload server.
     *
     * @return the LiveReload settings
     */
    @Nested
    public abstract LiveReload getLiveReload();

    /**
     * Configures the LiveReload server.
     *
     * @param action the configuration
     */
    public void liveReload(Action<? super LiveReload> action) {
        action.execute(getLiveReload());
    }

    /**
     * Test mode.
     *
     * @return the test mode settings
     * @since 5.0.3
     */
    @Nested
    public abstract TestMode getTest();

    /**
     * Configures test mode.
     *
     * @param action the configuration
     * @since 5.0.3
     */
    public void test(Action<? super TestMode> action) {
        action.execute(getTest());
    }

    /**
     * The LiveReload server the launcher starts when {@code micronaut-dev-livereload} is on the
     * development runtime classpath.
     */
    public abstract static class LiveReload {

        /**
         * Whether the LiveReload module is added to the development runtime classpath. Defaults to true.
         *
         * @return the flag
         */
        public abstract Property<Boolean> getEnabled();

        /**
         * The port the server listens on. Defaults to 35729.
         *
         * @return the port
         */
        public abstract Property<Integer> getPort();

        /**
         * Whether the client script is appended to HTML responses. Defaults to true; false for users
         * of a browser extension.
         *
         * @return the flag
         */
        public abstract Property<Boolean> getInjectScript();
    }

    /**
     * Test mode, {@code ./gradlew mnTest}: the tests a change affects run on a new generation in the
     * development JVM.
     *
     * @since 5.0.3
     */
    public abstract static class TestMode {

        /**
         * Which tests a change runs: {@code affected}, those that reference a changed class and those that
         * failed last, or {@code all}. Defaults to {@code affected}.
         *
         * @return the selection
         */
        public abstract Property<String> getSelection();

        /**
         * Whether every test runs once at startup. Defaults to true.
         *
         * @return the flag
         */
        public abstract Property<Boolean> getInitialRun();

        /**
         * The JUnit Platform configuration parameters, such as {@code junit.jupiter.execution.parallel.enabled}.
         *
         * @return the parameters
         */
        public abstract MapProperty<String, String> getParameters();

        /**
         * The path the LiveReload server serves the live HTML report at. Defaults to {@code /tests/}; the
         * launcher adds the leading and trailing slashes, and rejects {@code /}, {@code ..} and the server's
         * own paths.
         *
         * @return the path
         */
        public abstract Property<String> getReportPath();
    }
}
