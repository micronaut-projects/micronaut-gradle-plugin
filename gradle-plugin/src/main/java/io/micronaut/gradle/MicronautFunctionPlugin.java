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

import com.diffplug.gradle.eclipse.apt.AptEclipsePlugin;
import io.micronaut.gradle.docker.MicronautDockerPlugin;
import io.micronaut.gradle.graalvm.MicronautGraalPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginManager;

/**
 * A plugin for a Micronaut function.
 *
 * @author graemerocher
 * @since 5.0.0
 */
public class MicronautFunctionPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(MicronautMinimalFunctionPlugin.class);
        pluginManager.apply(AptEclipsePlugin.class);
        pluginManager.apply(MicronautDockerPlugin.class);
        pluginManager.apply(MicronautGraalPlugin.class);
    }

}
