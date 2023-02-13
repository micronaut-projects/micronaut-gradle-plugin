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
package io.micronaut.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import static io.micronaut.gradle.MicronautComponentPlugin.MICRONAUT_BOMS_CONFIGURATION;

public class MicronautBasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().create("micronaut", MicronautExtension.class);
        project.getConfigurations().create(MICRONAUT_BOMS_CONFIGURATION, conf -> {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(false);
            conf.setDescription("BOMs which will be applied by the Micronaut plugins");
        });
    }
}
