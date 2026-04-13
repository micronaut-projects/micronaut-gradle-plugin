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

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Provider;

import java.util.ArrayList;
import java.util.List;

public final class ApplicationPluginUtils {
    private ApplicationPluginUtils() {
    }

    public static List<String> applicationDefaultJvmArgs(Project project) {
        JavaApplication javaApplication = project.getExtensions().findByType(JavaApplication.class);
        if (javaApplication == null) {
            return List.of();
        }
        var jvmArgs = new ArrayList<String>();
        for (Object jvmArg : javaApplication.getApplicationDefaultJvmArgs()) {
            jvmArgs.add(String.valueOf(jvmArg));
        }
        return jvmArgs;
    }

    public static Provider<List<String>> applicationDefaultJvmArgsProvider(Project project) {
        return project.provider(() -> applicationDefaultJvmArgs(project));
    }
}
