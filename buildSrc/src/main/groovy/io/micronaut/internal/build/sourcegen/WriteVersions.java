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
package io.micronaut.internal.build.sourcegen;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public abstract class WriteVersions extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getVersions();

    @Input
    public abstract Property<String> getPackageName();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void writeVersions() throws IOException {
        File outputFile = getOutputDirectory().file(getPackageName().map(pkg -> pkg.replace('.', '/') + "/DefaultVersions.java")).get().getAsFile();
        Path parentPath = outputFile.getParentFile().toPath();
        if (Files.isDirectory(parentPath) || Files.createDirectories(parentPath) != null) {
            try (PrintWriter prn = new PrintWriter(new FileWriter(outputFile))) {
                prn.println("package " + getPackageName().get() + ";\n");
                prn.println();
                prn.println("/**");
                prn.println(" * Stores default versions used by the Gradle plugins.");
                prn.println(" */");
                prn.println("public class DefaultVersions {");
                for (Map.Entry<String, String> entry : getVersions().get().entrySet()) {
                    var module = entry.getKey().toUpperCase(Locale.US).replace('-', '_');
                    var version = entry.getValue();
                    prn.println("    public static final String " + module + " = \"" + version + "\";");
                }
                prn.println("}");
            }
        }
    }
}
