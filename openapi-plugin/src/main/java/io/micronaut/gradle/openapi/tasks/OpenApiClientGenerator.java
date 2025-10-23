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

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

@CacheableTask
public abstract class OpenApiClientGenerator extends AbstractOpenApiGenerator<OpenApiClientWorkAction, OpenApiClientWorkAction.ClientParameters> {

    @Optional
    @Input
    public abstract Property<String> getClientId();

    @Optional
    @Input
    public abstract Property<Boolean> getClientPath();

    @Optional
    @Input
    public abstract Property<String> getBasePathSeparator();

    @Optional
    @Input
    public abstract ListProperty<String> getAdditionalClientTypeAnnotations();

    @Optional
    @Input
    public abstract Property<Boolean> getUseAuth();

    @Optional
    @Input
    public abstract Property<String> getAuthorizationFilterPattern();

    @Optional
    @Input
    public abstract Property<Boolean> getGenerateAuthClasses();

    @Optional
    @Input
    public abstract Property<Boolean> getAuthFilter();

    @Optional
    @Input
    public abstract Property<Boolean> getUseOauth();

    @Optional
    @Input
    public abstract Property<Boolean> getUseBasicAuth();

    @Optional
    @Input
    public abstract Property<Boolean> getUseApiKeyAuth();

    @Optional
    @Input
    public abstract Property<String> getAuthorizationFilterPatternStyle();

    @Optional
    @Input
    public abstract ListProperty<String> getAuthFilterClientIds();

    @Optional
    @Input
    public abstract ListProperty<String> getAuthFilterExcludedClientIds();

    @Optional
    @Input
    public abstract Property<String> getAuthConfigName();

    @Override
    protected Class<OpenApiClientWorkAction> getWorkerAction() {
        return OpenApiClientWorkAction.class;
    }

    @Override
    protected void configureWorkerParameters(OpenApiClientWorkAction.ClientParameters params) {
        params.getClientId().set(getClientId());
        params.getClientPath().set(getClientPath());
        params.getBasePathSeparator().set(getBasePathSeparator());
        params.getAdditionalClientTypeAnnotations().set(getAdditionalClientTypeAnnotations());

        params.getUseAuth().set(getUseAuth());
        params.getAuthorizationFilterPattern().set(getAuthorizationFilterPattern());
        params.getGenerateAuthClasses().set(getGenerateAuthClasses());
        params.getAuthFilter().set(getAuthFilter());
        params.getUseOauth().set(getUseOauth());
        params.getUseBasicAuth().set(getUseBasicAuth());
        params.getUseApiKeyAuth().set(getUseApiKeyAuth());
        params.getAuthorizationFilterPatternStyle().set(getAuthorizationFilterPatternStyle());
        params.getAuthFilterClientIds().set(getAuthFilterClientIds());
        params.getAuthFilterExcludedClientIds().set(getAuthFilterExcludedClientIds());
        params.getAuthConfigName().set(getAuthConfigName());
    }
}
