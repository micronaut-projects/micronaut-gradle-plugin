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
package io.micronaut.gradle.fatjar;

import com.needhamsoftware.unojar.gradle.PackageUnoJarTask;
import com.needhamsoftware.unojar.gradle.UnoJarPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

public class FatJarPlugin implements Plugin<Project> {
    @Override
    public void apply(Project p) {
        var pluginManager = p.getPluginManager();
        pluginManager.apply(UnoJarPlugin.class);
        pluginManager.withPlugin("io.micronaut.aot", unused -> configureAotFatJar(p));
    }

    private void configureAotFatJar(Project p) {
        var tasks = p.getTasks();
        var aotJar = tasks.named("optimizedJitJar", Jar.class);
        tasks.register("packageOptimizedUnoJar", PackageUnoJarTask.class, unoJar -> {
            unoJar.getMainJar().convention(aotJar.flatMap(AbstractArchiveTask::getArchiveFile));
            unoJar.getArchiveClassifier().convention("unojar-optimized");
        });
    }
}
