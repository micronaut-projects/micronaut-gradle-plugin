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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;

public abstract class JsonSchemaFolderGenerator extends AbstractJsonSchemaGenerator<JsonSchemaFolderWorkAction, JsonSchemaFolderWorkAction.FolderParameters> {

    @InputDirectory
    public abstract DirectoryProperty getInputDirectory();

    @Override
    protected Class<JsonSchemaFolderWorkAction> getWorkerAction() {
        return JsonSchemaFolderWorkAction.class;
    }

    @Override
    protected void configureWorkerParameters(JsonSchemaFolderWorkAction.FolderParameters params) {
        params.getInputDirectory().set(getInputDirectory());
    }
}

