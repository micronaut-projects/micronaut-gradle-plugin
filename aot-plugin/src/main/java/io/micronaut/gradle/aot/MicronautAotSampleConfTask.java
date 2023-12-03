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
package io.micronaut.gradle.aot;

import io.micronaut.gradle.Strings;

import java.io.File;
import java.util.List;

public abstract class MicronautAotSampleConfTask extends AbstractMicronautAotCliTask {
    @Override
    protected void configureExtraArguments(List<String> args) {
        args.add("--config");
        File targetFile = getOutputDirectory().zip(getTargetRuntime(), (dir, runtime) -> dir.file(runtime.getSimpleName() + ".properties")).get().getAsFile();
        targetFile.getParentFile().mkdirs();
        args.add(targetFile.getAbsolutePath());
    }

    @Override
    protected void onSuccess(File outputDir) {
        File sampleFile = new File(outputDir, getTargetRuntime().map(runtime -> runtime.getSimpleName() + ".properties").orElse("sample.properties").get());
        if (sampleFile.exists()) {
            getLogger().lifecycle("Sample configuration file written to {}", Strings.clickableUrl(sampleFile));
        }
    }
}
