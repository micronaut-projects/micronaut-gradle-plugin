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

import io.micronaut.openapi.generator.MicronautCodeGeneratorEntryPoint;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;

@CacheableTask
public abstract class OpenApiServerGenerator extends AbstractOpenApiGenerator {
    @Input
    public abstract Property<String> getControllerPackage();

    @Input
    public abstract Property<Boolean> getUseAuth();

    @Override
    protected void configureBuilder(MicronautCodeGeneratorEntryPoint.Builder builder) {
        builder.forServer(spec -> {
            spec.withControllerPackage(getControllerPackage().get());
            spec.withAuthentication(getUseAuth().get());
            spec.withGenerateAbstractClasses(true);
            spec.withGenerateControllerFromExamples(false);
            spec.withGenerateOperationsToReturnNotImplemented(false);
        });
    }
}
