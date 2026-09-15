package io.micronaut.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Generates {@code ImportFactory} source files from dependency jars.
 *
 * @since 5.0.0
 */
@CacheableTask
public abstract class GenerateImportFactoryTask extends DefaultTask {
    private static final Pattern MULTI_RELEASE_JAR_PREFIX = Pattern.compile("^META-INF/versions/\\d+/");

    @Classpath
    public abstract ConfigurableFileCollection getDependencyJars();

    @Input
    public abstract Property<String> getIncludePackagesFilter();

    @Input
    public abstract Property<String> getExcludePackagesFilter();

    @Input
    @Optional
    public abstract Property<String> getTargetPackage();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void generate() {
        File outputDirectory = getOutputDirectory().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(outputDirectory));

        Set<String> packages = collectPackages();
        if (packages.isEmpty()) {
            return;
        }

        String targetPackage = normalizedTargetPackage();
        if (targetPackage == null) {
            for (String packageName : packages) {
                writeImportFactory(packageName, List.of(packageName));
            }
        } else {
            writeImportFactory(targetPackage, List.copyOf(packages));
        }
    }

    private Set<String> collectPackages() {
        Pattern includePackages = Pattern.compile(getIncludePackagesFilter().get());
        Pattern excludePackages = Pattern.compile(getExcludePackagesFilter().get());
        Set<String> packages = new TreeSet<>();
        for (File dependencyJar : getDependencyJars().getFiles()) {
            packages.addAll(readPackages(dependencyJar, includePackages, excludePackages));
        }
        return packages;
    }

    private Set<String> readPackages(File dependencyJar, Pattern includePackages, Pattern excludePackages) {
        try (JarFile jarFile = new JarFile(dependencyJar, false)) {
            Set<String> packages = new TreeSet<>();
            jarFile.stream()
                .map(this::packageNameOf)
                .filter(packageName -> packageName != null)
                .filter(includePackages.asMatchPredicate())
                .filter(excludePackages.asMatchPredicate().negate())
                .forEach(packages::add);
            return packages;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + dependencyJar, e);
        }
    }

    private String packageNameOf(JarEntry entry) {
        if (entry.isDirectory()) {
            return null;
        }
        String entryName = stripMultiReleaseJarPrefix(entry.getName());
        if (!entryName.endsWith(".class")) {
            return null;
        }
        int slashIndex = entryName.lastIndexOf('/');
        if (slashIndex <= 0) {
            return null;
        }
        String packageName = entryName.substring(0, slashIndex).replace('/', '.');
        if (!SourceVersion.isName(packageName)) {
            return null;
        }
        return packageName;
    }

    private String stripMultiReleaseJarPrefix(String entryName) {
        return MULTI_RELEASE_JAR_PREFIX.matcher(entryName).replaceFirst("");
    }

    private String normalizedTargetPackage() {
        if (!getTargetPackage().isPresent()) {
            return null;
        }
        String packageName = getTargetPackage().get().trim();
        if (packageName.isEmpty()) {
            return null;
        }
        if (!SourceVersion.isName(packageName)) {
            throw new IllegalArgumentException("Invalid target package: " + packageName);
        }
        return packageName;
    }

    private void writeImportFactory(String packageName, List<String> importedPackages) {
        Path factoryPath = getOutputDirectory().get().getAsFile().toPath()
            .resolve(packageName.replace('.', '/'))
            .resolve("ImportFactory.java");

        List<String> code = new ArrayList<>();
        code.add("package " + packageName + ";");
        code.add("");
        code.add("import io.micronaut.context.annotation.Factory;");
        code.add("import io.micronaut.context.annotation.Import;");
        code.add("");
        code.add("@Factory");
        code.add("@Import(");
        code.add("    packages = {");
        for (String importedPackage : importedPackages) {
            code.add("        \"" + importedPackage + "\",");
        }
        code.add("    }");
        code.add(")");
        code.add("public final class ImportFactory {");
        code.add("}");
        code.add("");

        try {
            Files.createDirectories(factoryPath.getParent());
            Files.write(factoryPath, code, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write " + factoryPath, e);
        }
    }
}
