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
package io.micronaut.gradle.internal;

import org.gradle.api.plugins.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;

/**
 * Builder class to model build dependencies.
 *
 * @author Sergio del Amo
 * @since 3.4.0
 */
public class Dependencies {
    private static final String DEVELOPMENT_ONLY = "developmentOnly";

    private final List<AutomaticDependency> dependencies;

    private Dependencies(List<AutomaticDependency> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, List<AutomaticDependency>> toMap() {
        return dependencies.stream()
                .collect(groupingBy(AutomaticDependency::configuration));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<AutomaticDependency> dependencies = new ArrayList<>();

        public Builder compileOnly(String coordinates) {
            return compileOnly(coordinates, null);
        }

        public Builder compileOnly(String coordinates, ConfigurableVersionProperty version) {
            this.dependencies.add(new AutomaticDependency(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, coordinates, Optional.ofNullable(version)));
            return this;
        }

        public Builder runtimeOnly(String coordinates) {
            return runtimeOnly(coordinates, null);
        }
        
        public Builder runtimeOnly(String coordinates, ConfigurableVersionProperty version) {
            this.dependencies.add(new AutomaticDependency(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, coordinates, Optional.ofNullable(version)));
            return this;
        }

        public Builder developmentOnly(String coordinates) {
            return developmentOnly(coordinates, null);
        }
        
        public Builder developmentOnly(String coordinates, ConfigurableVersionProperty version) {
            this.dependencies.add(new AutomaticDependency(DEVELOPMENT_ONLY, coordinates, Optional.ofNullable(version)));
            return this;
        }

        public Builder implementation(String coordinates) {
            return implementation(coordinates, null);
        }
        
        public Builder implementation(String coordinates, ConfigurableVersionProperty version) {
            this.dependencies.add(new AutomaticDependency(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, coordinates, Optional.ofNullable(version)));
            return this;
        }

        public Builder testImplementation(String coordinates) {
            return testImplementation(coordinates, null);
        }
        
        public Builder testImplementation(String coordinates, ConfigurableVersionProperty version) {
            this.dependencies.add(new AutomaticDependency(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, coordinates, Optional.ofNullable(version)));
            return this;
        }

        public Dependencies build() {
            return new Dependencies(Collections.unmodifiableList(dependencies));
        }
    }

}
