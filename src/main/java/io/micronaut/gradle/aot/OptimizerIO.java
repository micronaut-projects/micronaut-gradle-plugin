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
package io.micronaut.gradle.aot;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;

import java.util.Locale;

public interface OptimizerIO {
    @Classpath
    ConfigurableFileCollection getClasspath();

    @Input
    Property<MicronautAotOptimizerTask.TargetRuntime> getTargetRuntime();

    @Input
    Property<String> getTargetPackage();

    @OutputDirectory
    DirectoryProperty getOutputDirectory();

    enum TargetRuntime {
        JIT,
        NATIVE;

        public String getSimpleName() {
            return name().toLowerCase(Locale.US);
        }

        public String getCapitalizedName() {
            switch (this) {
                case JIT:
                    return "Jit";
                case NATIVE:
                    return "Native";
            }
            return name();
        }
    }
}
