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
import org.gradle.api.provider.Property;

public abstract class OpenApiServerWorkAction extends AbstractOpenApiWorkAction<OpenApiServerWorkAction.ServerParameters> {

    protected interface ServerParameters extends OpenApiParameters {

        Property<String> getControllerPackage();

        Property<Boolean> getUseAuth();

        Property<Boolean> getAot();

        Property<Boolean> getGenerateHardNullable();

        Property<Boolean> getGenerateStreamingFileUpload();
    }

    @Override
    protected void configureBuilder(MicronautCodeGeneratorBuilder builder) {
        var params = getParameters();

        if ("kotlin".equalsIgnoreCase(params.getLang().get())) {
            builder.forKotlinServer(spec -> spec
                .withControllerPackage(params.getControllerPackage().getOrNull())
                .withAuthentication(params.getUseAuth().get())
                .withAot(params.getAot().get())
                .withGenerateImplementationFiles(false)
                .withGenerateControllerFromExamples(false)
                .withGenerateOperationsToReturnNotImplemented(false)
                .withGeneratedAnnotation(params.getGeneratedAnnotation().get())
                .withFluxForArrays(params.getFluxForArrays().get())
                .withGenerateStreamingFileUpload(params.getGenerateStreamingFileUpload().get())
                .withKsp(params.getKsp().get())
                .withCoroutines(params.getCoroutines().get())
                .withJvmOverloads(params.getJvmOverloads().get())
                .withJvmRecord(params.getJvmRecord().get())
                .withJavaCompatibility(params.getJavaCompatibility().get())
                .withUserParameterMode(params.getUserParameterMode().get())
            );
        } else {
            builder.forJavaServer(spec -> spec
                .withControllerPackage(params.getControllerPackage().getOrNull())
                .withAuthentication(params.getUseAuth().get())
                .withAot(params.getAot().get())
                .withGenerateImplementationFiles(false)
                .withGenerateControllerFromExamples(false)
                .withGenerateOperationsToReturnNotImplemented(false)
                .withGeneratedAnnotation(params.getGeneratedAnnotation().get())
                .withLombok(params.getLombok().get())
                .withNoArgsConstructor(params.getNoArgsConstructor().get())
                .withFluxForArrays(params.getFluxForArrays().get())
                .withGenerateStreamingFileUpload(params.getGenerateStreamingFileUpload().get())
                .withGenerateHardNullable(params.getGenerateHardNullable().get())
                .withUserParameterMode(params.getUserParameterMode().get())
            );
        }
    }
}
