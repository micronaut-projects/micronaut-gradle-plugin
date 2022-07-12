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
package io.micronaut.internal.build.plugin;

import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;

import javax.inject.Inject;

public abstract class MicronautPluginExtension {

    private final GradlePluginDevelopmentExtension gradlePlugin;

    @Inject
    public MicronautPluginExtension(GradlePluginDevelopmentExtension gradlePlugin) {
        this.gradlePlugin = gradlePlugin;
    }

    public void register(String alias, String pluginClass, String description) {
        String[] parts = alias.split("\\.");
        for (int i=1; i<parts.length; i++) {
            parts[i] = StringGroovyMethods.capitalize(parts[i]);
        }
        String prettyName = String.join("", parts) + "Plugin";

        gradlePlugin.getPlugins().create(prettyName, decl -> {
            decl.setId("io.micronaut." + alias);
            decl.setImplementationClass(pluginClass);
            decl.setDescription(description);
            decl.setDisplayName(description);
        });
    }
}
