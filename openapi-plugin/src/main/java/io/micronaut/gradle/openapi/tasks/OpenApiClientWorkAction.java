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

    protected interface ClientParameters extends AbstractOpenApiWorkAction.OpenApiParameters {

        Property<String> getClientId();

        Property<Boolean> getClientPath();

        Property<String> getBasePathSeparator();

        ListProperty<String> getAdditionalClientTypeAnnotations();

        Property<Boolean> getUseAuth();

        Property<Boolean> getGenerateAuthClasses();

        Property<Boolean> getAuthFilter();

        Property<Boolean> getUseOauth();

        Property<Boolean> getUseBasicAuth();

        Property<Boolean> getUseApiKeyAuth();

        Property<String> getAuthorizationFilterPattern();

        Property<String> getAuthorizationFilterPatternStyle();

        ListProperty<String> getAuthFilterClientIds();

        ListProperty<String> getAuthFilterExcludedClientIds();

        Property<String> getAuthConfigName();
    }

    @Override
    protected void configureBuilder(MicronautCodeGeneratorBuilder builder) {
        var params = getParameters();
        if ("kotlin".equalsIgnoreCase(params.getLang().get())) {
            builder.forKotlinClient(spec -> spec
                .withAuthorization(params.getUseAuth().get())
                .withAdditionalClientTypeAnnotations(params.getAdditionalClientTypeAnnotations().getOrElse(List.of()))
                .withGeneratedAnnotation(params.getGeneratedAnnotation().get())
                .withFluxForArrays(params.getFluxForArrays().get())
                .withKsp(params.getKsp().get())
                .withAuthorizationFilterPattern(params.getAuthorizationFilterPattern().getOrNull())
                .withAuthorizationFilterPatternStyle(params.getAuthorizationFilterPatternStyle().getOrNull())
                .withGenerateAuthClasses(params.getGenerateAuthClasses().get())
                .withAuthFilter(params.getAuthFilter().get())
                .withUseOauth(params.getUseOauth().get())
                .withUseBasicAuth(params.getUseBasicAuth().get())
                .withUseApiKeyAuth(params.getUseApiKeyAuth().get())
                .withAuthFilterClientIds(params.getAuthFilterClientIds().getOrNull())
                .withAuthFilterExcludedClientIds(params.getAuthFilterExcludedClientIds().getOrNull())
                .withAuthConfigName(params.getAuthConfigName().getOrNull())
                .withClientId(params.getClientId().getOrNull())
                .withClientPath(params.getClientPath().get())
                .withBasePathSeparator(params.getBasePathSeparator().getOrNull())
                .withCoroutines(params.getCoroutines().get())
                .withJvmOverloads(params.getJvmOverloads().get())
                .withJvmRecord(params.getJvmRecord().get())
                .withJavaCompatibility(params.getJavaCompatibility().get()));
        } else {
            builder.forJavaClient(spec -> spec
                .withAuthorization(params.getUseAuth().get())
                .withAdditionalClientTypeAnnotations(params.getAdditionalClientTypeAnnotations().getOrElse(List.of()))
                .withLombok(params.getLombok().get())
                .withNoArgsConstructor(params.getNoArgsConstructor().get())
                .withGeneratedAnnotation(params.getGeneratedAnnotation().get())
                .withFluxForArrays(params.getFluxForArrays().get())
                .withAuthorizationFilterPattern(params.getAuthorizationFilterPattern().getOrNull())
                .withAuthorizationFilterPatternStyle(params.getAuthorizationFilterPatternStyle().getOrNull())
                .withGenerateAuthClasses(params.getGenerateAuthClasses().get())
                .withAuthFilter(params.getAuthFilter().get())
                .withUseOauth(params.getUseOauth().get())
                .withUseBasicAuth(params.getUseBasicAuth().get())
                .withUseApiKeyAuth(params.getUseApiKeyAuth().get())
                .withAuthFilterClientIds(params.getAuthFilterClientIds().getOrNull())
                .withAuthFilterExcludedClientIds(params.getAuthFilterExcludedClientIds().getOrNull())
                .withAuthConfigName(params.getAuthConfigName().getOrNull())
                .withClientId(params.getClientId().getOrNull())
                .withClientPath(params.getClientPath().get())
                .withBasePathSeparator(params.getBasePathSeparator().getOrNull()));
        }
    }

}
