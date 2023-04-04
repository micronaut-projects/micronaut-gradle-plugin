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
package io.micronaut.gradle;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;

import java.util.Set;

/**
 * Utilities to deal with Gradle configuration attributes.
 */
public abstract class AttributeUtils {
    private AttributeUtils() {

    }

    /**
     * Copies attributes from a source configuration to a target configuration
     * @param from the source configuration
     * @param to the target configuration
     */
    public static void copyAttributes(Configuration from, Configuration to) {
        from.attributes(attrs -> {
            AttributeContainer runtimeClasspathAttributes = to.getAttributes();
            Set<Attribute<?>> keySet = runtimeClasspathAttributes.keySet();
            for (Attribute<?> attribute : keySet) {
                //noinspection unchecked,DataFlowIssue
                attrs.attribute((Attribute<Object>) attribute, runtimeClasspathAttributes.getAttribute(attribute));
            }
        });
    }
}
