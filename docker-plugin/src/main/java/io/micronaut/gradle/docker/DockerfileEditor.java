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
package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.docker.editor.DefaultEditor;
import io.micronaut.gradle.docker.editor.Editor;
import io.micronaut.gradle.docker.editor.FingerprintingEditor;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.model.ObjectFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class DockerfileEditor {
    private DockerfileEditor() {

    }

    public static void apply(ObjectFactory objects, Dockerfile task, List<Action<? super Editor>> actions) {
        try {
            Path dockerFile = task.getDestFile().get().getAsFile().toPath();
            List<String> lines = Files.readAllLines(
                    dockerFile
            );
            DefaultEditor editor = objects.newInstance(DefaultEditor.class, objects, Optional.empty(), lines, Optional.empty(), Optional.empty());
            actions.forEach(tweak -> tweak.execute(editor));
            Files.write(dockerFile, editor.getLines());
        } catch (IOException e) {
            throw new GradleException("Unable to edit docker file", e);
        }
    }

    static List<String> fingerprintOf(ObjectFactory objects, List<Action<? super Editor>> actions) {
        FingerprintingEditor editor = objects.newInstance(FingerprintingEditor.class, objects, new ArrayList<String>());
        actions.forEach(tweak -> tweak.execute(editor));
        return editor.getFingerprint();
    }
}
