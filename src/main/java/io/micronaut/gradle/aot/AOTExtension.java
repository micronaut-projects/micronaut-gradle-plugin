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

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;

/**
 * Micronaut AOT extension, used to configure
 * the ahead-of-time/build time optimizations.
 */
public interface AOTExtension extends AOTOptimizations {
    /**
     * Allows configuring the Micronaut AOT version to use.
     * @return the Micronaut AOT version
     */
    @Optional
    @Input
    Property<String> getVersion();

    /**
     * Path to a user provided AOT configuration file. If not
     * provided, the plugin will use reasonable defaults. If
     * it's provided, the file will be read and merged with
     * options configured in this extension.
     * @return the configuration file
     */
    @Optional
    @InputFile
    RegularFileProperty getConfigFile();

}
