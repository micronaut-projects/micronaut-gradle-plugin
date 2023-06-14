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
package io.micronaut.gradle.openapi.tasks;

import io.micronaut.openapi.generator.MicronautCodeGeneratorBuilder;
import io.micronaut.openapi.generator.MicronautCodeGeneratorEntryPoint;
import io.micronaut.openapi.generator.SerializationLibraryKind;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.util.Locale;

public abstract class AbstractOpenApiGenerator extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getDefinitionFile();

    @Input
    public abstract Property<String> getInvokerPackageName();

    @Input
    public abstract Property<String> getApiPackageName();

    @Input
    public abstract Property<String> getModelPackageName();

    @Input
    public abstract Property<Boolean> getUseBeanValidation();

    @Input
    public abstract Property<Boolean> getUseOptional();

    @Input
    public abstract Property<Boolean> getUseReactive();

    @Input
    public abstract ListProperty<MicronautCodeGeneratorEntryPoint.OutputKind> getOutputKinds();

    @Input
    public abstract Property<String> getSerializationFramework();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public final void execute() {
        var builder = MicronautCodeGeneratorEntryPoint.builder()
                .withDefinitionFile(getDefinitionFile().get().getAsFile().toURI())
                .withOutputDirectory(getOutputDirectory().getAsFile().get())
                .withOutputs(
                        getOutputKinds().get().toArray(new MicronautCodeGeneratorEntryPoint.OutputKind[0])
                )
                .withOptions(options -> {
                    options.withInvokerPackage(getInvokerPackageName().get());
                    options.withApiPackage(getApiPackageName().get());
                    options.withModelPackage(getModelPackageName().get());
                    options.withBeanValidation(getUseBeanValidation().get());
                    options.withOptional(getUseOptional().get());
                    options.withReactive(getUseReactive().get());
                    options.withSerializationLibrary(SerializationLibraryKind.valueOf(getSerializationFramework().get().toUpperCase(Locale.US)));
                });
        configureBuilder(builder);
        builder.build().generate();
    }

    protected abstract void configureBuilder(MicronautCodeGeneratorBuilder builder);

}
