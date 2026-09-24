package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.ApplicationPluginUtils;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.docker.model.Layer;
import io.micronaut.gradle.docker.model.LayerKind;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class MicronautDockerfile extends Dockerfile implements DockerBuildOptions {
    public static final String DEFAULT_WORKING_DIR = "/home/app";
    public static final String DEFAULT_BASE_IMAGE = "eclipse-temurin:";

    @Input
    private final Property<String> baseImage;
    @Input
    private final ListProperty<String> args;
    @Input
    protected final ListProperty<Integer> exposedPorts;
    @Input
    protected final Property<DockerBuildStrategy> buildStrategy;
    @Input
    private final Property<String> defaultCommand;

    /**
     * @return The JDK version to use with native image. Defaults to the toolchain version, or the current Java version.
     */
    @Input
    public abstract Property<JavaVersion> getJdkVersion();

    @Input
    private final Property<String> targetWorkingDirectory;

    /**
     * The layers to copy to the image.
     * @return the layers
     */
    @Input
    public abstract ListProperty<Layer> getLayers();

    /**
     * If true, the COPY command will use --link option when copying files from the build context.
     * Defaults to false.
     * @return The use copy link property
     */
    @Input
    @Optional
    public abstract Property<Boolean> getUseCopyLink();

    /**
     * The JDK AOT cache that the image trains while it is built. The generated images get the
     * {@code micronaut.docker.jdkAotCache} options by default, and the cache is enabled there,
     * because the image layers depend on it.
     * @return the JDK AOT cache options
     * @since 5.0.3
     */
    @Nested
    public abstract JdkAotCacheOptions getJdkAotCache();

    public MicronautDockerfile() {
        Project project = getProject();
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a Docker File for a Micronaut application");
        ObjectFactory objects = project.getObjects();
        this.buildStrategy = objects.property(DockerBuildStrategy.class)
                                    .convention(DockerBuildStrategy.DEFAULT);
        this.baseImage = objects.property(String.class).convention("none");
        this.defaultCommand = objects.property(String.class).convention("none");
        this.args = objects.listProperty(String.class).convention(ApplicationPluginUtils.applicationDefaultJvmArgsProvider(project));
        this.exposedPorts = objects.listProperty(Integer.class)
                    .convention(Collections.singletonList(8080));
        this.targetWorkingDirectory = objects.property(String.class).convention(DEFAULT_WORKING_DIR);
        JavaPluginExtension javaExtension = PluginsHelper.javaPluginExtensionOf(project);
        getJdkVersion().convention(javaExtension.getTargetCompatibility());
        JdkAotCacheSupport.configureDefaults(getJdkAotCache());
    }

    @Override
    public Property<String> getTargetWorkingDirectory() {
        return targetWorkingDirectory;
    }

    @Override
    public Property<String> getDefaultCommand() {
        return defaultCommand;
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ProviderFactory getProviders();

    /**
     * The training script of the JDK AOT cache, next to the Dockerfile in the Docker context.
     * @return the training script, absent unless the JDK AOT cache is enabled
     */
    @OutputFile
    @Optional
    protected Provider<RegularFile> getJdkAotCacheTrainingScript() {
        return getJdkAotCache().getEnabled().flatMap(enabled -> Boolean.TRUE.equals(enabled)
            ? getDestDir().map(dir -> dir.file(JdkAotCacheSupport.TRAINING_SCRIPT))
            : getProviders().provider(() -> null));
    }

    @Input
    @Optional
    protected Provider<List<String>> getTweaks() {
        return getDockerfileTweaks().map(tweaks -> DockerfileEditor.fingerprintOf(getObjects(), tweaks));
    }

    @TaskAction
    @Override
    public void create() throws IOException {
        super.create();
        RegularFile trainingScript = getJdkAotCacheTrainingScript().getOrNull();
        if (trainingScript != null) {
            writeTrainingScript(trainingScript.getAsFile().toPath());
        }
        applyStandardTransforms(getUseCopyLink(), getObjects(), this);
        if (getDockerfileTweaks().isPresent()) {
            DockerfileEditor.apply(getObjects(), this, getDockerfileTweaks().get());
        }
        getLogger().lifecycle("Dockerfile written to: {}", getDestFile().get().getAsFile().getAbsolutePath());
    }

    public static void applyStandardTransforms(Provider<Boolean> useCopyLink, ObjectFactory objects, Dockerfile task) {
        if (Boolean.TRUE.equals(useCopyLink.getOrElse(true))) {
            DockerfileEditor.apply(objects, task, List.of(
                editor -> editor.replaceRegex("COPY (?!--link)(.*)", "COPY --link $1")
            ));
        }
    }

    private static void writeTrainingScript(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream script = MicronautDockerfile.class.getResourceAsStream(JdkAotCacheSupport.TRAINING_SCRIPT)) {
            if (script == null) {
                throw new GradleException("Unable to find the JDK AOT cache training script");
            }
            // The Dockerfile runs the script with bash, so it needs no execute permission
            Files.copy(script, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    protected void setupInstructions(List<Instruction> additionalInstructions) {
        String workDir = getTargetWorkingDirectory().get();
        DockerBuildStrategy buildStrategy = this.buildStrategy.getOrElse(DockerBuildStrategy.DEFAULT);
        boolean jdkAotCache = Boolean.TRUE.equals(getJdkAotCache().getEnabled().get());
        if (jdkAotCache) {
            validateJdkAotCache(buildStrategy);
        }
        JavaApplication javaApplication = getProject().getExtensions().getByType(JavaApplication.class);
        String from = getBaseImage().getOrNull();
        if ("none".equalsIgnoreCase(from)) {
            from = null;
        }
        switch (buildStrategy) {
            case ORACLE_FUNCTION:
                javaApplication.getMainClass().set("com.fnproject.fn.runtime.EntryPoint");
                from(new From("fnproject/fn-java-fdk:jre17-latest").withStage("fnfdk"));
                from(new Dockerfile.From(from != null ? from : DEFAULT_BASE_IMAGE + getDockerDefaultImageJavaTag()));
                copyFile(new CopyFile("/function/", "./function").withStage("fnfdk"));
                setupResources(this, getLayers().get(), "/function");
                String cmd = this.defaultCommand.get();
                if ("none".equals(cmd)) {
                    super.defaultCommand("io.micronaut.oraclecloud.function.http.HttpFunction::handleRequest");
                } else {
                    super.defaultCommand(cmd);
                }
                super.entryPoint(
                    "java",
                    "-XX:-UsePerfData",
                    "-XX:+UseSerialGC",
                    "-Xshare:auto",
                    "-Djava.awt.headless=true",
                    "-Djava.library.path=/function/runtime/lib",
                    "-cp",
                    "/function/libs/*:/function/runtime/*:/function/*:/function/resources",
                    "com.fnproject.fn.runtime.EntryPoint"
                );
                break;
            case LAMBDA:
                // JVM Lambda images share the standard layer layout; the entrypoint is specialized below.
            default:
                from(new Dockerfile.From(from != null ? from : DEFAULT_BASE_IMAGE + getDockerDefaultImageJavaTag()));
                setupResources(this, getLayers().get(), null);
                exposePort(exposedPorts);
                getInstructions().addAll(additionalInstructions);
                if (getInstructions().get().stream().noneMatch(instruction -> instruction.getKeyword().equals(EntryPointInstruction.KEYWORD))) {
                    if (jdkAotCache) {
                        setupJdkAotCacheTraining(workDir);
                    }
                    String cache = workDir + "/" + JdkAotCacheSupport.CACHE_FILE;
                    entryPoint(getArgs().map(strings -> {
                        var newList = new ArrayList<String>(strings.size() + 6);
                        newList.add("java");
                        if (jdkAotCache) {
                            newList.add("-XX:AOTCache=" + cache);
                            newList.addAll(JdkAotCacheSupport.sharedJvmFlags(strings));
                        }
                        newList.addAll(strings);
                        if (buildStrategy == DockerBuildStrategy.LAMBDA) {
                            newList.add("-cp");
                            newList.add(workDir + "/libs/*:" + workDir + "/resources:" + workDir + "/application.jar");
                            newList.add("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
                        } else {
                            newList.add("-jar");
                            newList.add(workDir + "/application.jar");
                        }
                        return newList;
                    }));
                } else if (jdkAotCache) {
                    throw new GradleException("The JDK AOT cache needs the ENTRYPOINT generated by the " + getName() + " task, but an ENTRYPOINT instruction was added to it");
                }
        }
    }

    private void validateJdkAotCache(DockerBuildStrategy buildStrategy) {
        DockerExtension docker = PluginsHelper.findMicronautExtension(getProject()).getExtensions().findByType(DockerExtension.class);
        if (docker == null || !Boolean.TRUE.equals(docker.getJdkAotCache().getEnabled().get())) {
            // The runner JARs and the resources layer only have a class path of JARs when the extension enables the cache
            throw new GradleException("The JDK AOT cache is enabled on the " + getName() + " task, but it must be enabled with micronaut.docker.jdkAotCache.enabled, because the image layers depend on it");
        }
        if (buildStrategy != DockerBuildStrategy.DEFAULT) {
            throw new GradleException("The JDK AOT cache only supports the " + DockerBuildStrategy.DEFAULT + " Docker build strategy, but the " + getName() + " task uses " + buildStrategy);
        }
        JavaVersion jdkVersion = getJdkVersion().get();
        if (!jdkVersion.isCompatibleWith(JavaVersion.VERSION_25)) {
            throw new GradleException("The JDK AOT cache needs JDK 25 or later, but the jdkVersion of the " + getName() + " task is " + jdkVersion);
        }
        JdkAotCacheSupport.validate(getJdkAotCache());
    }

    /**
     * Adds the training of the JDK AOT cache after the last instruction that the build adds: the image's
     * own {@code java} runs the application once with the arguments of the {@code ENTRYPOINT}.
     *
     * @param workDir the working directory of the image
     */
    private void setupJdkAotCacheTraining(String workDir) {
        copyFile(new CopyFile(JdkAotCacheSupport.TRAINING_SCRIPT, workDir + "/" + JdkAotCacheSupport.TRAINING_SCRIPT));
        // The dependencies of the image tell whether its Micronaut core has the training run switch
        Provider<Boolean> trainingRunSwitch = getLayers().map(layers -> JdkAotCacheSupport.hasTrainingRunSwitch(layers.stream()
            .filter(layer -> layer.getLayerKind().get() == LayerKind.LIBS || layer.getLayerKind().get() == LayerKind.SNAPSHOT_LIBS)
            .flatMap(layer -> layer.getFiles().getFiles().stream())
            .toList()));
        ListProperty<Integer> ports = getExposedPorts();
        JdkAotCacheOptions options = getJdkAotCache();
        runCommand(getArgs().zip(trainingRunSwitch, (args, useSwitch) -> JdkAotCacheSupport.execForm(
            JdkAotCacheSupport.trainingCommand(workDir, args, ports.get(), options, useSwitch)
        )));
    }

    /**
     * The Dockerfile task requires a 'from' at least, but this
     * will be replaced in setupTaskPostEvaluate where we also
     * incorporate commands supplied by the build.gradle file (if required)
     */
    public void setupDockerfileInstructions() {
        from("placeholder");
    }

    /**
     * This is executed post project evaluation
     */
    public void setupTaskPostEvaluate() {
        // Get any custom instructions the user may or may not have entered, but ignoring our 'from' placeholder
        var additionalInstructions = new ArrayList<>(getInstructions().get().subList(1, getInstructions().get().size()));
        // Reset the instructions to empty
        getInstructions().set(new ArrayList<>());
        setupInstructions(additionalInstructions);
    }

    /**
     * @return The build strategy.
     */
    public Property<DockerBuildStrategy> getBuildStrategy() {
        return buildStrategy;
    }

    @Override
    public ListProperty<String> getArgs() {
        return args;
    }

    @Override
    public Property<String> getBaseImage() {
        return baseImage;
    }

    @Override
    public ListProperty<Integer> getExposedPorts() {
        return this.exposedPorts;
    }

    @Override
    public DockerBuildOptions args(String... args) {
        this.args.addAll(args);
        return this;
    }

    @Override
    public DockerBuildOptions baseImage(String imageName) {
        if (imageName != null) {
            this.baseImage.set(imageName);
        }
        return this;
    }

    @Override
    public DockerBuildOptions exportPorts(Integer... ports) {
        this.exposedPorts.set(Arrays.asList(ports));
        return this;
    }

    private String getDockerDefaultImageJavaTag() {
        JavaVersion javaVersion = getJdkVersion().get();

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_25)) {
            return "25-jre";
        }
        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_21)) {
            return "21-jre";
        }
        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
            return "17-jre";
        }
        return "latest";
    }

    public static void setupResources(Dockerfile task, List<Layer> layers, String workDir) {
        final String finalWorkDir = workDir != null ? workDir : determineWorkingDir(task);
        task.workingDir(finalWorkDir);

        for (Layer layer : layers) {
            task.copyFile(task.getProject().provider(() -> {
                if (layer.getFiles().isEmpty()) {
                    return null;
                }
                var kind = layer.getLayerKind().get();
                return new CopyFile("layers/" + kind.sourceDirName(), finalWorkDir + "/" + kind.targetDirName());
            }));
        }
    }

    private static String determineWorkingDir(Dockerfile task) {
        String workDir = DEFAULT_WORKING_DIR;
        if (task instanceof DockerBuildOptions dbo) {
            workDir = dbo.getTargetWorkingDirectory().get();
        }
        return workDir;
    }
}
