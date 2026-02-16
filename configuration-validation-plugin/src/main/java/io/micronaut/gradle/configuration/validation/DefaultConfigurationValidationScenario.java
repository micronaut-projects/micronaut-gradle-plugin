/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.gradle.configuration.validation;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.List;

/**
 * Default implementation of {@link ConfigurationValidationScenario}.
 */
public abstract class DefaultConfigurationValidationScenario implements ConfigurationValidationScenario {

    /**
     * Creates a scenario with default conventions.
     *
     * @param objects object factory
     */
    @Inject
    public DefaultConfigurationValidationScenario(ObjectFactory objects) {
        getEnabled().convention(true);
        getOverrideClasspath().convention(false);
        getResourcesDirs().convention(List.of());
        getSuppressions().convention(List.of());
    }

    @Override
    public abstract Property<Boolean> getEnabled();

    @Override
    public abstract ListProperty<String> getEnvironments();

    @Override
    public abstract Property<Boolean> getFailOnNotPresent();

    @Override
    public abstract Property<Boolean> getDeduceEnvironments();

    @Override
    public abstract Property<String> getFormat();

    @Override
    public abstract ListProperty<String> getSuppressions();

    @Override
    public abstract DirectoryProperty getOutputDirectory();

    @Override
    public abstract Property<Boolean> getOverrideClasspath();

    @Override
    public abstract ConfigurableFileCollection getClasspath();

    @Override
    public abstract ListProperty<String> getResourcesDirs();
}
