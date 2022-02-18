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
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import static com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.SHADOW_GROUP;

/**
 * A support class which is separate from the main code just to avoid eagerly
 * loading Shadow classes which might not be present if the user didn't apply
 * the plugin.
 */
class AotShadowSupport {

    static void registerShadowJar(Project project,
                                  ArchiveOperations archiveOperations,
                                  TaskContainer tasks,
                                  TaskProvider<Jar> optimizedJar) {
        project.afterEvaluate(unused -> {
            JavaApplication javaApplication = project.getExtensions().findByType(JavaApplication.class);
            TaskProvider<ShadowJar> shadowProvider = tasks.register(optimizedJar.getName() + "All", ShadowJar.class, shadow -> {
                shadow.setGroup(SHADOW_GROUP);
                shadow.setDescription("Creates a fat jar including the Micronaut AOT optimizations");
                shadow.getArchiveClassifier().convention("all-optimized");
                Jar mainJar = tasks.named("jar", Jar.class).get();
                shadow.getManifest().inheritFrom(mainJar.getManifest());
                if (javaApplication != null) {
                    // This is the reason why we use an afterEvalute:
                    // The shadow plugin apparently does something with attributes,
                    // breaking support for providers
                    shadow.getManifest().getAttributes().put("Main-Class", javaApplication.getMainClass().get());
                }
                shadow.from(optimizedJar.map(jar -> archiveOperations.zipTree(jar.getArchiveFile().get())));
                shadow.getConfigurations().add(project.getConfigurations().findByName("runtimeClasspath"));
                shadow.getExcludes().addAll(tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME, ShadowJar.class).get().getExcludes());
            });
            tasks.named("assemble").configure(assemble -> assemble.dependsOn(shadowProvider));
        });
    }
}
