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
package io.micronaut.gradle;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Project;

public class ShadowPluginSupport {
    public static final String SHADOW_PLUGIN = "com.github.johnrengelman.shadow";

    public static void withShadowPlugin(Project p, Runnable action) {
        p.getPluginManager().withPlugin(SHADOW_PLUGIN, unused -> action.run());
    }

    /**
     * If shadow JAR is enabled, it configures to merge all META-INF/services file into a single file otherwise this will break the application.
     * <a href="https://imperceptiblethoughts.com/shadow/configuration/merging/#merging-service-descriptor-files">Shadow: Merging Server Descriptor Files</a>
     * @param project Gradle Project
     */
    public static void mergeServiceFiles(Project project) {
        project.getTasks().withType(ShadowJar.class).configureEach(ShadowJar::mergeServiceFiles);
    }
}
