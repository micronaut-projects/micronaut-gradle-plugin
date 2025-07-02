/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Gradle task that generates Micronaut @Import factories for beans from dependencies.
 */
public abstract class GenerateImportFactoryTask extends DefaultTask {
    @Input
    @Optional
    public abstract Property<Configuration> getResolvedConfiguration();
    @Input
    @Optional
    public abstract Property<Boolean> getTaskEnabled();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourcesDir();

    @Input
    @Optional
    public abstract Property<String> getIncludeDependenciesFilter();

    @Input
    @Optional
    public abstract Property<String> getExcludeDependenciesFilter();

    @Input
    @Optional
    public abstract Property<String> getIncludePackagesFilter();

    @Input
    @Optional
    public abstract Property<String> getExcludePackagesFilter();

    @Input
    @Optional
    public abstract Property<String> getTargetPackage();

    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    public GenerateImportFactoryTask() {
        // Set default values
        getTaskEnabled().convention(false);
        getGeneratedSourcesDir().convention(getProject().getLayout().getBuildDirectory().dir("generated-sources/importfactory"));
        getIncludeDependenciesFilter().convention("^.*:.*$");
        getExcludeDependenciesFilter().convention("^$");
        getIncludePackagesFilter().convention("^.*$");
        getExcludePackagesFilter().convention("^$");
    }

    /*This is the main action method of the task, annotated with @TaskAction. Gradle executes this method when the task runs.*/
    @TaskAction
    public void generate() throws IOException {
        if (!getTaskEnabled().get()) {
            getLogger().debug("Task disabled");
            return;
        }

        Set<File> classpathFiles = getRuntimeClasspath().getFiles();
        getLogger().info("Task enabled: {}", getTaskEnabled().get());
        getLogger().info("Runtime classpath files (before filtering):\n- {}",
                classpathFiles.stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining("\n- ")));

        getLogger().lifecycle("Scanning runtime classpath: {}",
                classpathFiles.stream()
                        .map(File::getName)
                        .collect(Collectors.joining(", ")));

        List<File> dependencies = resolveFilteredDependencies(getResolvedConfiguration().isPresent() ? getResolvedConfiguration().get() : null, classpathFiles);
        getLogger().info("Filtered dependencies resolved by resolveFilteredDependencies():\n- {}",
                dependencies.stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining("\n- ")));

        if (dependencies.isEmpty()) {
            getLogger().warn("No matching dependencies found in:\n{}",
                    classpathFiles.stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.joining("\n")));
            return;
        }

        List<String> packages = resolveFilteredPackages(dependencies);
        getLogger().info("Filtered packages resolved by resolveFilteredPackages():\n- {}",
                String.join("\n- ", packages));
        if (packages.isEmpty()) {
            getLogger().warn("No packages found in dependencies:\n{}",
                    dependencies.stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.joining("\n")));
            return;
        }

        getLogger().lifecycle("Found packages to generate factories for:\n- {}",
                String.join("\n- ", packages));

        generateFactoryFiles(packages);
    }

    /*This helper method is responsible for filtering the raw classpath files based on the configured dependency regex patterns.*/
    private List<File> resolveFilteredDependencies(Configuration configurationForMetadata, Set<File> classpathFilesFromTaskInput) {
        getLogger().info("--- Entering resolveFilteredDependencies ---");
        getLogger().info("IncludeDependenciesFilter: '{}'", getIncludeDependenciesFilter().get());
        getLogger().info("ExcludeDependenciesFilter: '{}'", getExcludeDependenciesFilter().get());

        Pattern includePattern = Pattern.compile(getIncludeDependenciesFilter().get());
        Pattern excludePattern = Pattern.compile(getExcludeDependenciesFilter().get());

        List<File> filteredFiles = new ArrayList<>();
        boolean artifactFilteringAttempted = false;

        // 1. Attempt to filter using ResolvedArtifact metadata if Configuration is provided
        if (configurationForMetadata != null) {
            getLogger().debug("Attempting to filter using ResolvedArtifacts from provided Configuration.");
            artifactFilteringAttempted = true;
            try {
                // Access resolved artifacts via the passed-in Configuration
                for (ResolvedArtifact artifact : configurationForMetadata.getResolvedConfiguration().getResolvedArtifacts()) {
                    File file = artifact.getFile();
                    String artifactIdentifier = artifact.getModuleVersion().getId().toString(); // e.g., "group:name:version"
                    String fileName = file.getName();
                    getLogger().debug("Processing resolved artifact: ID='{}', File='{}'", artifactIdentifier, fileName);

                    // Try matching against artifact identifier first, then filename
                    boolean includedByArtifactId = includePattern.matcher(artifactIdentifier).matches();
                    boolean excludedByArtifactId = excludePattern.matcher(artifactIdentifier).matches();

                    boolean includedByFileName = includePattern.matcher(fileName).matches();
                    boolean excludedByFileName = excludePattern.matcher(fileName).matches();

                    getLogger().debug("  Matches by Artifact ID: Included={}, Excluded={}", includedByArtifactId, excludedByArtifactId);
                    getLogger().debug("  Matches by File Name: Included={}, Excluded={}", includedByFileName, excludedByFileName);

                    boolean included = (includedByArtifactId || includedByFileName);
                    boolean excluded = (excludedByArtifactId || excludedByFileName);

                    if (included && !excluded) {
                        filteredFiles.add(file);
                        getLogger().debug("  -> INCLUDING dependency: {} ({})", artifactIdentifier, fileName);
                    } else {
                        getLogger().debug("  -> EXCLUDING dependency: {} ({})", artifactIdentifier, fileName);
                    }
                }
            } catch (Exception e) {
                getLogger().warn("Could not resolve artifacts from provided Configuration for filtering. Falling back to file-name-only filtering. Error: " + e.getMessage());
                filteredFiles.clear();
                artifactFilteringAttempted = false;
            }
        } else {
            getLogger().warn("No Configuration object provided for artifact metadata filtering. Proceeding with file-name-only filtering.");
        }

        if (!artifactFilteringAttempted || filteredFiles.isEmpty()) {
            getLogger().debug("Performing full classpath file-name-only filtering (or fallback).");
            for (File file : classpathFilesFromTaskInput) {
                if (!filteredFiles.contains(file)) {
                    String fileName = file.getName();
                    getLogger().debug("Processing direct file: {}", fileName);

                    boolean included = includePattern.matcher(fileName).matches();
                    boolean excluded = excludePattern.matcher(fileName).matches();
                    getLogger().debug("  File '{}': Included={}, Excluded={}", fileName, included, excluded);

                    if (included && !excluded) {
                        filteredFiles.add(file);
                        getLogger().debug("  -> INCLUDING direct file: {}", fileName);
                    } else {
                        getLogger().debug("  -> EXCLUDING direct file: {}", fileName);
                    }
                }
            }
        } else {
            getLogger().debug("Skipping full classpath file-name-only filtering as artifact filtering was successful and populated files.");
        }

        getLogger().info("--- Exiting resolveFilteredDependencies. Total filtered files: {} ---", filteredFiles.size());
        return filteredFiles;
    }

    /*This helper method scans the already-filtered dependency JARs for Java package names and applies the package-level regex filters.*/
    private List<String> resolveFilteredPackages(List<File> dependencies) {
        getLogger().info("--- Entering resolveFilteredPackages ---");
        String includePatternString = getIncludePackagesFilter().get();
        String excludePatternString = getExcludePackagesFilter().get();
        getLogger().info("IncludePackagesFilter: '{}'", includePatternString);
        getLogger().info("ExcludePackagesFilter: '{}'", excludePatternString);
        Pattern include = Pattern.compile(includePatternString);
        Pattern exclude = Pattern.compile(excludePatternString);

        Set<String> allPackages = new HashSet<>(); // Use a set to collect all unique packages first
        for (File file : dependencies) {
            try {
                List<String> packagesInFile = getPackagesFromFile(file);
                getLogger().info("Packages found in {}: {}", file.getName(), packagesInFile); // Log packages per file
                allPackages.addAll(packagesInFile);
            } catch (IOException e) {
                getLogger().warn("Failed to read packages from {}: {}", file, e.getMessage());
            }
        }

        getLogger().info("All unique packages before filtering: {}", allPackages);

        return allPackages.stream()
                .filter(pkg -> {
                    boolean matchesInclude = include.matcher(pkg).matches();
                    boolean matchesExclude = exclude.matcher(pkg).matches();
                    getLogger().info("Processing package '{}': matchesInclude={}, matchesExclude={}", pkg, matchesInclude, matchesExclude);
                    return matchesInclude && !matchesExclude; // Combined filter logic
                })
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /*This helper method extracts unique package names from a given JAR file*/
    private List<String> getPackagesFromFile(File file) throws IOException {
        getLogger().info("--- Entering getPackagesFromFile for file: {} ---", file.getName());
        if (!file.exists()) {
            getLogger().warn("File does not exist: {}", file.getAbsolutePath());
            return Collections.emptyList();
        }

        if (!file.getName().endsWith(".jar")) {
            getLogger().debug("Skipping non-JAR file: {}", file.getName());
            return Collections.emptyList();
        }

        Set<String> packages = new HashSet<>();
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String pkg = extractPackageName(entry);
                if (pkg != null) {
                    packages.add(pkg);
                    getLogger().debug("  Extracted package: '{}' from entry: '{}'", pkg, entry.getName());
                }
            }
        } catch (IOException e) {
            getLogger().warn("Failed to read JAR file {}: {}", file.getName(), e.getMessage());
            throw e;
        }
        getLogger().info("--- Exiting getPackagesFromFile. Found {} unique packages in {} ---", packages.size(), file.getName());
        return new ArrayList<>(packages);
    }

    /*This helper method takes a JarEntry (a file within a JAR) and attempts to derive a Java package name from its path*/
    private String extractPackageName(JarEntry entry) {
        String path = entry.getName();
        if (path.endsWith(".class") && !path.contains("$")) { // Skip inner classes
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash > 0) {
                return path.substring(0, lastSlash).replace('/', '.');
            }
        }
        return null;
    }

    /*This method orchestrates the creation of the actual Java source files for the ImportFactory*/
    private void generateFactoryFiles(List<String> packages) throws IOException {
        Path outputDir = getGeneratedSourcesDir().get().getAsFile().toPath();
        String targetPkg = getTargetPackage().getOrNull();

        if (targetPkg == null || targetPkg.isEmpty()) {
            for (String pkg : packages) {
                generateFactoryFile(outputDir, pkg, Collections.singletonList(pkg));
            }
        } else {
            generateFactoryFile(outputDir, targetPkg, packages);
        }
    }

    /*This method handles the actual writing of a single ImportFactory.java file to disk*/
    private void generateFactoryFile(Path outputDir, String packageName, List<String> importedPackages)
            throws IOException {
        Path packageDir = outputDir.resolve(packageName.replace('.', '/'));
        Path factoryFile = packageDir.resolve("ImportFactory.java");

        String factoryCode = generateFactoryCode(packageName, importedPackages);

        Files.createDirectories(packageDir);
        Files.write(factoryFile, factoryCode.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        getLogger().info("Generated factory file: {}", factoryFile);
    }

    /*This method constructs the full Java source code string for the ImportFactory class*/
    private String generateFactoryCode(String packageName, List<String> importedPackages) {
        String imports = String.join(",\n",
                importedPackages.stream()
                        .map(pkg -> "\"" + pkg + "\"")
                        .collect(Collectors.toList()));

        return "package " + packageName + ";\n\n" +
                "import io.micronaut.context.annotation.Factory;\n" +
                "import io.micronaut.context.annotation.Import;\n" +
                "import jakarta.annotation.Generated;\n\n" +
                "/**\n" +
                " * Micronaut import factory for packages: " + String.join(", ", importedPackages) + "\n" +
                " */\n" +
                "@Generated(\"io.micronaut.gradle:micronaut-gradle-plugin\")\n" +
                "@Factory\n" +
                "@Import(packages = {\n" +
                "        " + imports + "\n" +
                "})\n" +
                "public class ImportFactory {}\n";
    }
}