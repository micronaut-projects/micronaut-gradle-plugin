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
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.List;

public abstract class OpenApiClientWorkAction extends AbstractOpenApiWorkAction<OpenApiClientWorkAction.ClientParameters> {
    interface ClientParameters extends AbstractOpenApiWorkAction.OpenApiParameters {
        Property<String> getClientId();

        Property<Boolean> getUseAuth();

        Property<String> getAuthorizationFilterPattern();

        Property<String> getBasePathSeparator();

        ListProperty<String> getAdditionalClientTypeAnnotations();
    }

    protected void configureBuilder(MicronautCodeGeneratorBuilder builder) {
        var parameters = getParameters();
        builder.forClient(spec -> {
            spec.withAuthorization(parameters.getUseAuth().get());
            if (parameters.getClientId().isPresent()) {
                spec.withClientId(parameters.getClientId().get());
            }
            spec.withAdditionalClientTypeAnnotations(parameters.getAdditionalClientTypeAnnotations().getOrElse(List.of()));
            if (parameters.getBasePathSeparator().isPresent()) {
                spec.withBasePathSeparator(parameters.getBasePathSeparator().get());
            }
            if (parameters.getAuthorizationFilterPattern().isPresent()) {
                spec.withAuthorizationFilterPattern(parameters.getAuthorizationFilterPattern().get());
            }
        });
    }

}
