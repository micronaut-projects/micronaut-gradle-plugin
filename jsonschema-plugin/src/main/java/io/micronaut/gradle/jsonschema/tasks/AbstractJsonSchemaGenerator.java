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
package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.nio.file.Path;

public abstract class AbstractJsonSchemaGenerator<W extends AbstractJsonSchemaWorkAction<P>, P extends AbstractJsonSchemaWorkAction.JsonSchemaParameters> extends DefaultTask {

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    @Optional
    public abstract Property<String> getLanguage();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    @Optional
    public abstract Property<String> getPackageName();

    @Input
    @Optional
    public abstract Property<String> getOutputFileName();

    @Input
    @Optional
    public abstract ListProperty<String> getAcceptedUrlPatterns();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Internal
    protected abstract Class<W> getWorkerAction();

    protected abstract void configureWorkerParameters(P params);

    @TaskAction
    public final void execute() {
        getWorkerExecutor().classLoaderIsolation(spec -> spec.getClasspath().from(getClasspath()))
                .submit(getWorkerAction(), params -> {
                    params.getLang().set(getLanguage());
                    params.getAcceptedUrlPatterns().set(getAcceptedUrlPatterns());
                    params.getPackageName().set(getPackageName());
                    params.getOutputFileName().set(getOutputFileName());
                    params.getOutputDirectory().set(getOutputDirectory().getOrElse(getProject().getLayout().getBuildDirectory().dir("generated/jsonSchema").get()));
                    configureWorkerParameters(params);
                });
    }
}

