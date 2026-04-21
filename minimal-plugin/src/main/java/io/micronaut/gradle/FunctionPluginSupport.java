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

import org.gradle.api.Project;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;

import java.util.Locale;

/**
 * Shared helpers for function-oriented plugin behavior.
 */
public final class FunctionPluginSupport {
    public static final String MINIMAL_FUNCTION_PLUGIN_ID = "io.micronaut.minimal.function";
    public static final String FUNCTION_PLUGIN_ID = "io.micronaut.function";
    public static final String APPLICATION_PLUGIN_ID = "application";

    private static final String FUNCTION_PROJECT_MARKER = "io.micronaut.internal.function-project";
    private static final String APPLICATION_BRIDGE_PROPERTY = "application";

    private FunctionPluginSupport() {
    }

    public static void markFunctionProject(Project project) {
        project.getExtensions().getExtraProperties().set(FUNCTION_PROJECT_MARKER, true);
    }

    public static boolean isFunctionProject(Project project) {
        var extraProperties = project.getExtensions().getExtraProperties();
        return (extraProperties.has(FUNCTION_PROJECT_MARKER) && Boolean.TRUE.equals(extraProperties.get(FUNCTION_PROJECT_MARKER)))
            || project.getPluginManager().hasPlugin(MINIMAL_FUNCTION_PLUGIN_ID)
            || project.getPluginManager().hasPlugin(FUNCTION_PLUGIN_ID);
    }

    public static void configureFunctionRuntime(Project project) {
        var applicationBridge = ensureApplicationBridge(project);
        if (applicationBridge != null) {
            project.getPluginManager().withPlugin(APPLICATION_PLUGIN_ID, unused ->
                applicationBridge.attach(project.getExtensions().getByType(JavaApplication.class))
            );
        }
        var micronautExtension = PluginsHelper.findMicronautExtension(project);
        micronautExtension.onRuntimeConfigured(runtime -> maybeApplyApplicationPlugin(project, runtime));
        var configuredRuntime = findConfiguredRuntime(project);
        if (configuredRuntime != null) {
            maybeApplyApplicationPlugin(project, configuredRuntime);
        }
        project.afterEvaluate(unused -> maybeApplyApplicationPlugin(project, PluginsHelper.resolveRuntime(project)));
    }

    public static boolean usesApplicationLambdaRuntime(Project project) {
        return !isFunctionProject(project) || project.getPluginManager().hasPlugin(APPLICATION_PLUGIN_ID);
    }

    private static void maybeApplyApplicationPlugin(Project project, MicronautRuntime runtime) {
        if (runtime != null && runtime != MicronautRuntime.LAMBDA_JAVA && runtime != MicronautRuntime.NONE) {
            project.getPluginManager().apply(ApplicationPlugin.class);
        }
    }

    private static MicronautRuntime findConfiguredRuntime(Project project) {
        Object configuredRuntime = project.findProperty("micronaut.runtime");
        if (configuredRuntime == null) {
            return null;
        }
        return MicronautRuntime.valueOf(configuredRuntime.toString().toUpperCase(Locale.ENGLISH));
    }

    private static FunctionApplicationBridge ensureApplicationBridge(Project project) {
        if (project.getExtensions().findByType(JavaApplication.class) != null) {
            return null;
        }
        var extraProperties = project.getExtensions().getExtraProperties();
        if (extraProperties.has(APPLICATION_BRIDGE_PROPERTY)) {
            Object existing = extraProperties.get(APPLICATION_BRIDGE_PROPERTY);
            if (existing instanceof FunctionApplicationBridge bridge) {
                return bridge;
            }
            return null;
        }
        var bridge = new FunctionApplicationBridge(project);
        extraProperties.set(APPLICATION_BRIDGE_PROPERTY, bridge);
        return bridge;
    }
}
