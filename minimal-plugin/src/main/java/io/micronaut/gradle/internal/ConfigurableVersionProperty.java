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
package io.micronaut.gradle.internal;

import org.codehaus.groovy.runtime.StringGroovyMethods;

/**
 * A configurable version property is a property which is found
 * in the DSL, as a type-safe accessor, allowing to override a
 * particular version of a dependency which is automatically
 * added by the Gradle plugin.
 *
 * @param name the simple name of the property
 * @param dslName the name in the DSL
 * @param gradlePropertyName the name of the gradle property
 */
public record ConfigurableVersionProperty(
        String name,
        String dslName,
        String gradlePropertyName
) {
    public static ConfigurableVersionProperty of(String name) {
        return new ConfigurableVersionProperty(
                name,
                name + "Version",
                "micronaut" + StringGroovyMethods.capitalize(name) + "Version"
        );
    }
}
