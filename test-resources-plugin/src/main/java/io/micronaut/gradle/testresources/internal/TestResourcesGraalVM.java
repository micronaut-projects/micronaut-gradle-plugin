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
package io.micronaut.gradle.testresources.internal;

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.tasks.NativeRunTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskContainer;

/**
 * Methods for Micronaut GraalVM plugin integration,
 * separated to make sure we don't run into classloading
 * issues.
 */
public final class TestResourcesGraalVM {
    public static void configure(Project project, TaskContainer tasks, Configuration testResourcesClasspathConfig) {
        // The native "run" tasks are not JavaExec tasks so we need to handle them separately
        tasks.withType(NativeRunTask.class).configureEach(t -> {
            GraalVMExtension graalVMExtension = project.getExtensions().findByType(GraalVMExtension.class);
            graalVMExtension.getBinaries().all(b -> b.getClasspath().from(testResourcesClasspathConfig));
        });
    }
}
