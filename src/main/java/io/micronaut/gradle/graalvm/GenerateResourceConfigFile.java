/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.graalvm;

import io.micronaut.gradle.graalvm.internal.ResourceConfigJsonWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
public abstract class GenerateResourceConfigFile extends DefaultTask {

    private static final String META_INF = "META-INF";
    private static final String PATTERN = "pattern";
    private static final String RESOURCE_CONFIG_JSON = "resource-config.json";
    private static final List<String> EXCLUDED_META_INF_DIRECTORIES = Arrays.asList("native-image", "services");

    public GenerateResourceConfigFile() {
        getOutputDirectory().convention(
                getProject().getLayout().getBuildDirectory().dir("generated/resources/graalvm")
        );
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceDirectories();

    /**
     * Configures the list of directories to search for resources, but are typically
     * unmanaged (because generated) so require more filtering than the application
     * resources.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMixedContentsDirectories();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void executeTask() {
        Set<String> resourcesToAdd = new HashSet<>();

        getResourceDirectories()
                .getFiles()
                .forEach(resourceDirectory -> resourcesToAdd.addAll(findResourceFiles(resourceDirectory)));

        getMixedContentsDirectories()
                .getFiles()
                .forEach(dirtyResourcesDir -> {
                    Path metaInfPath = Paths.get(dirtyResourcesDir.getAbsolutePath(), META_INF);

                    // Generated resources (like openapi)
                    resourcesToAdd.addAll(findResourceFiles(metaInfPath.toFile(), Collections.singletonList(META_INF)));
                });

        List<Map<String, String>> resourceList = resourcesToAdd.stream()
                .map(GenerateResourceConfigFile::mapToGraalResource)
                .collect(Collectors.toList());

        generateJsonFile(resourceList);
    }

    private void generateJsonFile(List<Map<String, String>> resourceList) {
        try {
            Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
            Files.createDirectories(outputDirectory);
            File resourceConfigFile = outputDirectory.resolve(RESOURCE_CONFIG_JSON).toFile();
            System.out.println("Generating " + resourceConfigFile.getAbsolutePath());
            ResourceConfigJsonWriter.generateJsonFile(resourceList, new FileOutputStream(resourceConfigFile));

        } catch (IOException e) {
            throw new GradleException("There was an error generating GraalVM resource-config.json file", e);
        }
    }

    private static Set<String> findResourceFiles(File folder) {
        return findResourceFiles(folder, null);
    }

    private static Set<String> findResourceFiles(File folder, List<String> filePath) {
        Set<String> resourceFiles = new HashSet<>();

        if (filePath == null) {
            filePath = new ArrayList<>();
        }

        if (folder.exists()) {
            File[] files = folder.listFiles();

            if (files != null) {
                boolean isMetaInfDirectory = META_INF.equals(folder.getName());

                for (File element : files) {
                    boolean isExcludedDirectory = EXCLUDED_META_INF_DIRECTORIES.contains(element.getName());
                    // Exclude some directories in 'META-INF' like 'native-image' and 'services' but process other
                    // 'META-INF' files and directories, for example, to include swagger-ui.
                    if (!isMetaInfDirectory || !isExcludedDirectory) {
                        if (element.isDirectory()) {
                            List<String> paths = new ArrayList<>(filePath);
                            paths.add(element.getName());

                            resourceFiles.addAll(findResourceFiles(element, paths));
                        } else {
                            String joinedDirectories = String.join("/", filePath);
                            String elementName = joinedDirectories.isEmpty() ? element.getName() : joinedDirectories + "/" + element.getName();

                            resourceFiles.add(elementName);
                        }
                    }
                }
            }
        }

        return resourceFiles;
    }

    private static Map<String, String> mapToGraalResource(String resourceName) {
        if (resourceName.contains("*")) {
            return Collections.singletonMap(PATTERN, resourceName);
        } else {
            return Collections.singletonMap(PATTERN, "\\Q" + resourceName + "\\E");
        }
    }
}
