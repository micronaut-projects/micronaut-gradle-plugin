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
package io.micronaut.gradle.aot;

import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin;
import com.github.jengelman.gradle.plugins.shadow.tasks.InheritManifest;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.lang.reflect.InvocationTargetException;

import static com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.SHADOW_GROUP;

/**
 * A support class which is separate from the main code just to avoid eagerly
 * loading Shadow classes which might not be present if the user didn't apply
 * the plugin.
 */
class AotShadowSupport {

    private static final Action<Object> NO_ACTION = unused -> { };

    private AotShadowSupport() {
    }

    static void registerShadowJar(Project project,
                                  ArchiveOperations archiveOperations,
                                  TaskContainer tasks,
                                  TaskProvider<Jar> optimizedJar) {
        project.afterEvaluate(unused -> {
            JavaApplication javaApplication = project.getExtensions().findByType(JavaApplication.class);
            var taskName = optimizedJar.getName() + "All";
            TaskProvider<ShadowJar> shadowProvider = tasks.register(taskName, ShadowJar.class, shadow -> {
                shadow.setGroup(SHADOW_GROUP);
                shadow.setDescription("Creates a fat jar including the Micronaut AOT optimizations");
                shadow.getArchiveClassifier().convention("all-optimized");
                Jar mainJar = tasks.named("jar", Jar.class).get();
                var shadowManifest = shadow.getManifest();
                var mainManifest = mainJar.getManifest();
                compatInheritFrom(shadowManifest, mainManifest);
                if (javaApplication != null) {
                    // This is the reason why we use an afterEvaluate:
                    // The shadow plugin apparently does something with attributes,
                    // breaking support for providers
                    shadowManifest.getAttributes().put("Main-Class", javaApplication.getMainClass().get());
                }
                shadow.from(optimizedJar.map(jar -> archiveOperations.zipTree(jar.getArchiveFile().get())));
                compatAddConfiguration(shadow, project.getConfigurations().findByName("runtimeClasspath"));
                shadow.getExcludes().addAll(tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME, ShadowJar.class).get().getExcludes());
            });
            tasks.named("assemble").configure(assemble -> assemble.dependsOn(shadowProvider));
        });
    }

    private static void compatAddConfiguration(ShadowJar shadowJar, Configuration configuration) {
        try {
            shadowJar.getConfigurations().add(configuration);
        } catch (Throwable ex) {
            try {
                var getConfigurationsMethod = shadowJar.getClass().getDeclaredMethod("getConfigurations");
                var result = getConfigurationsMethod.invoke(shadowJar);
                if (result instanceof SetProperty setProperty) {
                    setProperty.add(configuration);
                } else {
                    throw new RuntimeException("Unexpected return type for getConfigurations():" + result.getClass());
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void compatInheritFrom(InheritManifest shadowManifest, Manifest mainManifest) {
        try {
            shadowManifest.inheritFrom(mainManifest);
        } catch (Throwable ex) {
            // Workaround from compatibility with Shadow Plugin rewritten in Kotlin
            try {
                var inheritFromMethod = shadowManifest.getClass().getDeclaredMethod("inheritFrom", Object[].class, Action.class);
                inheritFromMethod.invoke(shadowManifest, new Object[] { mainManifest }, NO_ACTION);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
