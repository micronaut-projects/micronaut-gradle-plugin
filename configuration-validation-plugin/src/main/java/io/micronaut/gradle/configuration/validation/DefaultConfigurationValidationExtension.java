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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

import javax.inject.Inject;
import java.util.List;

/**
 * Default implementation of {@link ConfigurationValidationExtension}.
 */
public abstract class DefaultConfigurationValidationExtension implements ConfigurationValidationExtension {
    private final ConfigurationValidationScenario run;
    private final ConfigurationValidationScenario test;
    private final ConfigurationValidationScenario production;

    /**
     * Creates the extension and initializes scenario defaults.
     *
     * @param project the owning project
     */
    @Inject
    public DefaultConfigurationValidationExtension(Project project) {
        ObjectFactory objects = project.getObjects();

        getEnabled().convention(true);
        getFailOnNotPresent().convention(true);
        getDeduceEnvironments().convention(false);
        getFormat().convention("both");
        getSuppressions().convention(List.of());
        getProjectBaseDir().convention(project.getLayout().getProjectDirectory());
        getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("reports/micronaut/config-validation"));

        this.run = objects.newInstance(DefaultConfigurationValidationScenario.class);
        this.test = objects.newInstance(DefaultConfigurationValidationScenario.class);
        this.production = objects.newInstance(DefaultConfigurationValidationScenario.class);

        // Scenario defaults (mirrors Maven plugin behavior).
        run.getEnvironments().convention(List.of("dev"));
        test.getEnvironments().convention(List.of("test"));
        production.getEnvironments().convention(List.of());

        // Inherit global defaults.
        inheritDefaults(run);
        inheritDefaults(test);
        inheritDefaults(production);
    }

    private void inheritDefaults(ConfigurationValidationScenario scenario) {
        scenario.getFailOnNotPresent().convention(getFailOnNotPresent());
        scenario.getDeduceEnvironments().convention(getDeduceEnvironments());
        scenario.getFormat().convention(getFormat());
    }

    @Override
    public abstract Property<String> getVersion();

    @Override
    public abstract Property<Boolean> getEnabled();

    @Override
    public abstract DirectoryProperty getOutputDirectory();

    @Override
    public abstract Property<Boolean> getFailOnNotPresent();

    @Override
    public abstract Property<Boolean> getDeduceEnvironments();

    @Override
    public abstract Property<String> getFormat();

    @Override
    public abstract ListProperty<String> getSuppressions();

    @Override
    public abstract DirectoryProperty getProjectBaseDir();

    @Override
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Override
    public abstract Property<String> getJavaExecutable();

    @Override
    public final ConfigurationValidationScenario getRun() {
        return run;
    }

    /**
     * Configure the {@code run} validation scenario.
     *
     * @param action configuration action
     */
    public final void run(Action<? super ConfigurationValidationScenario> action) {
        action.execute(getRun());
    }

    @Override
    public final ConfigurationValidationScenario getTest() {
        return test;
    }

    /**
     * Configure the {@code test} validation scenario.
     *
     * @param action configuration action
     */
    public final void test(Action<? super ConfigurationValidationScenario> action) {
        action.execute(getTest());
    }

    @Override
    public final ConfigurationValidationScenario getProduction() {
        return production;
    }

    /**
     * Configure the {@code assemble} (production) validation scenario.
     *
     * @param action configuration action
     * @since 5.0.0
     */
    public final void assemble(Action<? super ConfigurationValidationScenario> action) {
        action.execute(getProduction());
    }
}
