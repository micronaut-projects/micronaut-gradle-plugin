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

    @Input
    @Optional
    public abstract Property<String> getClientId();

    @Input
    @Optional
    public abstract Property<Boolean> getClientPath();

    @Input
    public abstract Property<Boolean> getUseAuth();

    @Input
    @Optional
    public abstract Property<String> getAuthorizationFilterPattern();

    @Input
    @Optional
    public abstract Property<String> getBasePathSeparator();

    @Input
    public abstract ListProperty<String> getAdditionalClientTypeAnnotations();

    @Override
    protected Class<OpenApiClientWorkAction> getWorkerAction() {
        return OpenApiClientWorkAction.class;
    }

    @Override
    protected void configureWorkerParameters(OpenApiClientWorkAction.ClientParameters params) {
        params.getClientId().set(getClientId());
        params.getClientPath().set(getClientPath());
        params.getUseAuth().set(getUseAuth());
        params.getAuthorizationFilterPattern().set(getAuthorizationFilterPattern());
        params.getBasePathSeparator().set(getBasePathSeparator());
        params.getAdditionalClientTypeAnnotations().set(getAdditionalClientTypeAnnotations());
    }
}
