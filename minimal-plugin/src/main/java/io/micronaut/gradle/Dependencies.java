/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.gradle;

import org.gradle.api.plugins.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder class to model build dependencies.
 * @author Sergio del Amo
 * @since 3.4.0
 */
public class Dependencies {
    private static final String DEVELOPMENT_ONLY = "developmentOnly";

    private final List<String> developmentOnlyDependencies;
    private final List<String> implementationDependencies;
    private final List<String> testImplementationDependencies;
    private final List<String> runtimeOnlyDependencies;
    private final List<String> compileOnlyDependencies;

    private Dependencies(List<String> developmentOnlyDependencies,
                        List<String> implementationDependencies,
                        List<String> testImplementationDependencies,
                         List<String> runtimeOnlyDependencies,
                         List<String> compileOnlyDependencies) {
        this.developmentOnlyDependencies = developmentOnlyDependencies;
        this.implementationDependencies = implementationDependencies;
        this.testImplementationDependencies = testImplementationDependencies;
        this.runtimeOnlyDependencies = runtimeOnlyDependencies;
        this.compileOnlyDependencies = compileOnlyDependencies;
    }

    public Map<String, List<String>> toMap() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        if (isNotEmpty(developmentOnlyDependencies)) {
            m.put(DEVELOPMENT_ONLY, developmentOnlyDependencies);
        }
        if (isNotEmpty(implementationDependencies)) {
            m.put(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, implementationDependencies);
        }
        if (isNotEmpty(testImplementationDependencies)) {
            m.put(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, testImplementationDependencies);
        }
        if (isNotEmpty(runtimeOnlyDependencies)) {
            m.put(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, runtimeOnlyDependencies);
        }
        if (isNotEmpty(compileOnlyDependencies)) {
            m.put(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, compileOnlyDependencies);
        }
        return m;
    }

    private static boolean isNotEmpty(Collection<String> col) {
        return col != null && !col.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<String> developmentOnlyDependencies = new ArrayList<>();
        private final List<String> implementationDependencies = new ArrayList<>();
        private final List<String> testImplementationDependencies = new ArrayList<>();
        private final List<String> runtimeOnlyDependencies = new ArrayList<>();
        private final List<String> compileOnlyDependencies = new ArrayList<>();

        public Builder compileOnly(String coordinate) {
            this.compileOnlyDependencies.add(coordinate);
            return this;
        }

        public Builder runtimeOnly(String coordinate) {
            this. runtimeOnlyDependencies.add(coordinate);
            return this;
        }

        public Builder developmentOnly(String coordinate) {
            this.developmentOnlyDependencies.add(coordinate);
            return this;
        }

        public Builder implementation(String coordinate) {
            this.implementationDependencies.add(coordinate);
            return this;
        }

        public Builder testImplementation(String coordinate) {
            this.testImplementationDependencies.add(coordinate);
            return this;
        }

        public Dependencies build() {
            return new Dependencies(developmentOnlyDependencies,
                    implementationDependencies,
                    testImplementationDependencies,
                    runtimeOnlyDependencies,
                    compileOnlyDependencies);
        }
    }
}
