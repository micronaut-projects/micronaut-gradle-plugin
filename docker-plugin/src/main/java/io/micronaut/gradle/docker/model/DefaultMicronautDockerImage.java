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
package io.micronaut.gradle.docker.model;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public abstract class DefaultMicronautDockerImage implements MicronautDockerImage {

    private final String name;

    @Inject
    public DefaultMicronautDockerImage(String name) {
        this.name = name;
    }

    @NotNull
    @Override
    @Internal
    public String getName() {
        return name;
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    @Override
    public void addLayer(Action<? super Layer> spec) {
        Layer layer = getObjects().newInstance(Layer.class);
        layer.getRuntimeKind().convention(RuntimeKind.ANY);
        spec.execute(layer);
        getLayers().add(layer);
    }
}
