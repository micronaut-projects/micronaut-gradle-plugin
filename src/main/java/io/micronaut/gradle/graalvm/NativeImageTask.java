package io.micronaut.gradle.graalvm;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.AbstractExecTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * A gradle task for building a native image.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class NativeImageTask extends AbstractExecTask<NativeImageTask>
        implements NativeImageOptions {
    private final Property<String> imageName;
    private final Property<String> main;
    private final MapProperty<String, Object> systemProperties;
    private @Nullable FileCollection classpath;
    private final ListProperty<String> jvmArgs;
    private final Property<Boolean> isDebug;
    private final Property<Boolean> isFallback;
    private final Property<Boolean> isVerbose;
    private final Map<BooleanSupplier, String> booleanCmds;
    private final ConfigurableFileCollection configDirectories;

    /**
     * Default constructor.
     */
    public NativeImageTask() {
        super(NativeImageTask.class);
        String nativeImageExecutable = findNativeImage("GRAALVM_HOME", "JAVA_HOME");
        setExecutable(nativeImageExecutable);
        setWorkingDir(new File(getProject().getBuildDir(), "native-image"));
        ObjectFactory objectFactory = getObjectFactory();
        this.imageName = objectFactory.property(String.class)
                .convention("application");
        this.main = objectFactory.property(String.class);
        this.jvmArgs = objectFactory.listProperty(String.class)
                .convention(new ArrayList<>(5));
        this.systemProperties = objectFactory.mapProperty(String.class, Object.class)
                .convention(new LinkedHashMap<>(5));
        this.isDebug = objectFactory.property(Boolean.class).convention(false);
        this.isFallback = objectFactory.property(Boolean.class).convention(false);
        this.isVerbose = objectFactory.property(Boolean.class).convention(false);
        this.booleanCmds = new LinkedHashMap<>(3);
        this.classpath = objectFactory.fileCollection();
        this.configDirectories = objectFactory.fileCollection();
        booleanCmds.put(isDebug::get, "-H:GenerateDebugInfo=1");
        booleanCmds.put(() -> !isFallback.get(), "--no-fallback");
        booleanCmds.put(isVerbose::get,  "--verbose");
    }

    private String findNativeImage(String graalHome, String javaHome) {
        if (isNotBlank(graalHome)) {
            graalHome = System.getenv(graalHome);
            if (isNotBlank(graalHome)) {
                try {
                    final File f = getNativeImageExecutable(graalHome);
                    if (f.exists()) {
                        return f.getCanonicalPath();
                    }
                } catch (IOException e) {
                    // continue
                }
            }
        }

        if (isNotBlank(javaHome)) {
            javaHome = System.getenv(javaHome);
            if (isNotBlank(javaHome)) {
                final File f = getNativeImageExecutable(javaHome);
                if (f.exists()) {
                    try {
                        return f.getCanonicalPath();
                    } catch (IOException e) {
                        // continue
                    }
                }
            }
        }
        return "native-image";
    }

    @NotNull
    private File getNativeImageExecutable(String javaHome) {
        final File f;
        if (Os.isFamily("windows")) {
            f = new File(javaHome, "bin/native-image.exe");
            if (f.exists()) {
                return f;
            } else {
                return new File(javaHome, "lib/svm/bin/native-image.exe");
            }
        } else {
            f = new File(javaHome, "bin/native-image");
            if (f.exists()) {
                return f;
            } else {
                return new File(javaHome, "lib/svm/bin/native-image");
            }
        }
    }

    private boolean isNotBlank(String graalHome) {
        return graalHome != null && graalHome.length() > 0;
    }

    @Override
    public Property<Boolean> isFallback() {
        return isFallback;
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @OutputFile
    public File getNativeImageOutput() {
        return new File(getWorkingDir(), imageName.get());
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getConfigDirectories() {
        return configDirectories;
    }

    @Override
    protected void exec() {
        if (!GraalUtil.isGraalJVM()) {
            throw new RuntimeException("A GraalVM SDK is required to build native images");
        }
        configure(true);
        super.exec();
        System.out.println("Native Image written to: " + getNativeImageOutput());
    }

    /**
     * Configure the task.
     */
    public void configure(boolean includeClasspath) {
        // set the classpath
        final Project project = getProject();
        Configuration runtimeConfig = project
                .getConfigurations()
                .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        SourceSetContainer sourceSets = project
                .getExtensions()
                .getByType(SourceSetContainer.class);
        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (includeClasspath) {

            final SourceSetOutput output = mainSourceSet.getOutput();

            final Set<File> resolved = runtimeConfig.getResolvedConfiguration().getFiles();
            FileCollection classpathCollection = project.files(resolved);
            FileCollection outputDirs = output.getClassesDirs();
            classpathCollection = classpathCollection.plus(outputDirs);
            classpathCollection = classpathCollection.plus(project.files(output.getResourcesDir()));
            FileCollection cp = getClasspath();
            if (cp != null) {
                classpathCollection = classpathCollection.plus(cp);
            }

            String classpath = classpathCollection.getAsPath();
            if (classpath.length() > 0) {
                args("-cp", classpath);
            }
        }

        // set the main class
        if (!getMain().isPresent()) {
            throw new IllegalStateException("Main class must be set");
        }
        String main = getMain().get();
        args("-H:Class=" + main);

        // set the image name
        String imageName = getImageName().get();
        args("-H:Name=" + imageName);

        Set<File> configurationDirectories = getConfigDirectories().getFiles();
        if (!configurationDirectories.isEmpty()) {
            String configPath = configurationDirectories.stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining(","));
            args("-H:ConfigurationFileDirectories=" + configPath);
        }
        // Adds boolean flags to the command line
        booleanCmds.forEach((property, cmd) -> {
            if (property.getAsBoolean()) {
                args(cmd);
            }
        });


        Map<String, Object> sysProps = getSystemProperties().get();
        sysProps.forEach((n, v) -> {
            if (v != null) {
                args("-D" + n + "=\"" + v + "\"");
            }
        });

        List<String> jvmArgs = getJvmArgs().get();
        for (String jvmArg : jvmArgs) {
            args("-J" + jvmArg);
        }
    }

    @Override
    public Property<String> getImageName() {
        return this.imageName;
    }

    @Override
    public NativeImageOptions setImageName(@Nullable String name) {
        if (name != null) {
            this.imageName.set(name);
        }
        return this;
    }

    @Override
    public Property<String> getMain() {
        return this.main;
    }

    @Override
    public NativeImageOptions setMain(String main) {
        if (main != null) {
            this.main.set(main);
        }
        return this;
    }

    @Override
    public MapProperty<String, Object> getSystemProperties() {
        return systemProperties;
    }

    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        if (properties == null) {
            this.systemProperties.set(new LinkedHashMap<>());
        } else {
            this.systemProperties.set(properties);
        }
    }

    @Override
    public NativeImageOptions systemProperties(Map<String, ?> properties) {
        setSystemProperties(properties);
        return this;
    }

    @Override
    public NativeImageOptions systemProperty(String name, Object value) {
        if (name != null && value != null) {
            this.systemProperties.get().put(name, value.toString());
        }
        return this;
    }

    @Override
    public NativeImageOptions classpath(Object... paths) {
        this.classpath = getProject().files(paths);
        return this;
    }

    @Override
    public FileCollection getClasspath() {
        return this.classpath;
    }

    @Override
    public NativeImageOptions setClasspath(FileCollection classpath) {
        this.classpath = classpath;
        return this;
    }

    @Override
    public ListProperty<String> getJvmArgs() {
        return this.jvmArgs;
    }

    @Override
    public void setJvmArgs(@Nullable List<String> arguments) {
        if (arguments == null) {
            this.jvmArgs.set(new ArrayList<>(5));
        } else {
            this.jvmArgs.addAll(arguments);
        }
    }

    @Override
    public void setJvmArgs(@Nullable Iterable<?> arguments) {
        if (arguments == null) {
            this.jvmArgs.set(Collections.emptyList());
        } else {
            for (Object argument : arguments) {
                if (argument != null) {
                    this.jvmArgs.add(argument.toString());
                }
            }
        }
    }

    @Override
    public NativeImageOptions jvmArgs(Iterable<?> arguments) {
        setJvmArgs(arguments);
        return this;
    }

    @Override
    public NativeImageOptions jvmArgs(Object... arguments) {
        setJvmArgs(Arrays.asList(arguments));
        return this;
    }

    @Override
    public NativeImageOptions verbose(boolean verbose) {
        isVerbose.set(verbose);
        return this;
    }

    @Override
    public NativeImageOptions enableServerBuild(boolean enabled) {
        isVerbose.set(enabled);
        return this;
    }

    @Override
    public NativeImageOptions debug(boolean debug) {
        isDebug.set(debug);
        return this;
    }

    @Override
    public NativeImageOptions fallback(boolean fallback) {
        isFallback.set(fallback);
        return this;
    }

    @Override
    public Property<Boolean> isDebug() {
        return isDebug;
    }

    @Override
    public Property<Boolean> isVerbose() {
        return isVerbose;
    }
}
