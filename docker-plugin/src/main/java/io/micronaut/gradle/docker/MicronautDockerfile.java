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

    private final JdkAotCacheOptions jdkAotCacheOptions;

    /**
     * Whether the CRaC plugin is applied, whose images are built from the layers of the main image.
     */
    private final Property<Boolean> cracApplied;

    /**
     * The platform of the image build, when the build sets one.
     */
    private final Property<String> buildPlatform;

    private final Provider<Boolean> jdkAotCacheActive;

    /**
     * Whether the build adds an ENTRYPOINT, known once the instructions are set up.
     */
    private boolean entryPointAdded;

    /**
     * Why the cache is not trained by default, or null when it is, once decided.
     */
    private String jdkAotCacheDefaultGuard;
    private boolean jdkAotCacheDefaultDecided;

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
        this.jdkAotCacheOptions = objects.newInstance(JdkAotCacheOptions.class);
        JdkAotCacheSupport.configureDefaults(jdkAotCacheOptions);
        this.cracApplied = objects.property(Boolean.class).convention(false);
        this.buildPlatform = objects.property(String.class);
        this.jdkAotCacheActive = jdkAotCacheOptions.getEnabled()
            .orElse(project.getProviders().provider(() -> jdkAotCacheDefaultGuard() == null));
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
     * The JDK AOT cache that the image trains while it is built. It is not an option of the task:
     * {@link MicronautDockerPlugin} sets its conventions from {@code micronaut.docker.jdkAotCache}, which
     * users configure, because the image layers depend on it. The getter is concrete, because Gradle
     * cannot implement an abstract package-private getter for a subclass in another package, such as
     * the final Dockerfile task of the CRaC plugin.
     *
     * @return the JDK AOT cache options of this task
     */
    @Nested
    JdkAotCacheOptions getJdkAotCache() {
        return jdkAotCacheOptions;
    }

    /**
     * The training script of the JDK AOT cache, next to the Dockerfile in the Docker context.
     * @return the training script, absent unless the JDK AOT cache is enabled
     */
    @OutputFile
    @Optional
    Provider<RegularFile> getJdkAotCacheTrainingScript() {
        return jdkAotCacheActive().flatMap(enabled -> Boolean.TRUE.equals(enabled)
            ? getDestDir().map(dir -> dir.file(JdkAotCacheSupport.TRAINING_SCRIPT))
            : getObjects().fileProperty());
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
        logJdkAotCache();
        applyStandardTransforms(getUseCopyLink(), getObjects(), this);
        if (getDockerfileTweaks().isPresent()) {
            DockerfileEditor.apply(getObjects(), this, getDockerfileTweaks().get());
        }
        getLogger().lifecycle("Dockerfile written to: {}", getDestFile().get().getAsFile().getAbsolutePath());
    }

    /**
     * Whether the image trains a JDK AOT cache: the enabled option when it is set, and otherwise the
     * default, which trains one unless a guard of {@link #jdkAotCacheDefaultGuard()} applies.
     *
     * @return whether the image trains a JDK AOT cache
     */
    Provider<Boolean> jdkAotCacheActive() {
        return jdkAotCacheActive;
    }

    /**
     * @return whether the CRaC plugin is applied, set by {@link MicronautDockerPlugin}
     */
    Property<Boolean> jdkAotCacheCracApplied() {
        return cracApplied;
    }

    /**
     * @return the platform of the image build, set by {@link MicronautDockerPlugin}
     */
    Property<String> jdkAotCacheBuildPlatform() {
        return buildPlatform;
    }

    /**
     * Why the image does not train a JDK AOT cache by default: the default trains one only where the
     * plugin can do so without changing what the build asks for. Decided once, when it is first asked.
     *
     * @return the reason, or null when the image trains a cache by default
     */
    String jdkAotCacheDefaultGuard() {
        if (!jdkAotCacheDefaultDecided) {
            jdkAotCacheDefaultGuard = computeJdkAotCacheDefaultGuard();
            jdkAotCacheDefaultDecided = true;
        }
        return jdkAotCacheDefaultGuard;
    }

    private String computeJdkAotCacheDefaultGuard() {
        if (Boolean.TRUE.equals(cracApplied.get())) {
            return "the CRaC plugin builds its images from the layers of the main image";
        }
        DockerBuildStrategy strategy = buildStrategy.getOrElse(DockerBuildStrategy.DEFAULT);
        if (strategy != DockerBuildStrategy.DEFAULT) {
            return "the Docker build strategy is " + strategy;
        }
        JavaVersion jdkVersion = getJdkVersion().get();
        if (!jdkVersion.isCompatibleWith(JavaVersion.VERSION_25)) {
            return "the jdkVersion is " + jdkVersion + ", and the cache needs JDK 25 or later";
        }
        if (!"none".equalsIgnoreCase(getBaseImage().getOrElse("none"))) {
            return "the base image is not the default one, so the plugin cannot tell whether it has bash and JDK 25 or later";
        }
        if (entryPointAdded) {
            return "the build adds an ENTRYPOINT";
        }
        if (buildPlatform.isPresent()) {
            return "the image build sets a platform, which may not be the one of the Docker daemon";
        }
        if (jdkAotCacheOptions.getTrainingMode().isPresent() || !jdkAotCacheOptions.getTrainingPaths().get().isEmpty()) {
            return "trainingMode or trainingPaths are set, which only apply when enabled is true";
        }
        if (jdkAotCacheTrainingRunSupport().get() != JdkAotCacheSupport.TrainingRunSupport.MODE) {
            return "the application's Micronaut version has no " + JdkAotCacheTrainingMode.LOAD + " training mode, which needs Micronaut Core 5.3.0 or later";
        }
        return null;
    }

    private void logJdkAotCache() {
        if (getJdkAotCache().getEnabled().isPresent()) {
            if (Boolean.TRUE.equals(getJdkAotCache().getEnabled().get())) {
                JdkAotCacheSupport.logTrainingMode(getLogger(), getJdkAotCache(), jdkAotCacheTrainingRunSupport().get());
            }
            return;
        }
        String guard = jdkAotCacheDefaultGuard();
        if (guard == null) {
            getLogger().lifecycle("JDK AOT cache: the image trains one by default, with a training run that loads the bean definitions of the application and does not start it. "
                + "If the training fails, the image has no cache. Set micronaut.docker.jdkAotCache.enabled to false to skip it");
        } else {
            getLogger().info("JDK AOT cache: not trained by default, because {}. Set micronaut.docker.jdkAotCache.enabled to true to train one", guard);
        }
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
        Boolean jdkAotCacheEnabled = getJdkAotCache().getEnabled().getOrNull();
        boolean jdkAotCache = Boolean.TRUE.equals(jdkAotCacheEnabled);
        // Not set: the default decides once the instructions are known, when the Dockerfile is written
        boolean jdkAotCacheByDefault = jdkAotCacheEnabled == null;
        entryPointAdded = additionalInstructions.stream().anyMatch(instruction -> EntryPointInstruction.KEYWORD.equals(instruction.getKeyword()));
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
                    if (jdkAotCache || jdkAotCacheByDefault) {
                        setupJdkAotCacheTraining(workDir, jdkAotCacheByDefault);
                    }
                    String cache = workDir + "/" + JdkAotCacheSupport.CACHE_FILE;
                    Provider<Boolean> trainsByDefault = getProviders().provider(() -> jdkAotCacheByDefault && jdkAotCacheDefaultGuard() == null);
                    entryPoint(getArgs().zip(trainsByDefault, (strings, byDefault) -> {
                        var newList = new ArrayList<String>(strings.size() + 6);
                        newList.add("java");
                        if (jdkAotCache) {
                            newList.add("-XX:AOTCache=" + cache);
                            newList.addAll(JdkAotCacheSupport.sharedJvmFlags(strings));
                        } else if (Boolean.TRUE.equals(byDefault)) {
                            // Empty when the training fell back to an image without a cache
                            newList.add("@" + workDir + "/" + JdkAotCacheSupport.CACHE_ARGUMENTS_FILE);
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
        if (docker == null || !Boolean.TRUE.equals(docker.getJdkAotCache().getEnabled().getOrNull())) {
            // The runner JARs and the resources layer only have a class path of JARs when the extension enables the cache.
            // The options of the task are not public, but a Groovy build script can still reach them
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
    private void setupJdkAotCacheTraining(String workDir, boolean byDefault) {
        // By default, the instructions are only written when no guard applies
        Provider<Boolean> trains = byDefault
            ? getProviders().provider(() -> jdkAotCacheDefaultGuard() == null)
            : getProviders().provider(() -> true);
        copyFile(trains.map(on -> on
            ? new CopyFile(JdkAotCacheSupport.TRAINING_SCRIPT, workDir + "/" + JdkAotCacheSupport.TRAINING_SCRIPT)
            : null));
        ListProperty<Integer> ports = getExposedPorts();
        JdkAotCacheOptions options = getJdkAotCache();
        runCommand(trains.flatMap(on -> on
            ? getArgs().zip(jdkAotCacheTrainingRunSupport(), (args, support) -> JdkAotCacheSupport.execForm(
                JdkAotCacheSupport.trainingCommand(workDir, args, ports.get(), options, support, byDefault)))
            : getProviders().provider(() -> null)));
    }

    /**
     * The dependencies of the image tell what its Micronaut core offers for a training run.
     *
     * @return the support for a training run
     */
    private Provider<JdkAotCacheSupport.TrainingRunSupport> jdkAotCacheTrainingRunSupport() {
        return getLayers().map(layers -> JdkAotCacheSupport.trainingRunSupport(layers.stream()
            .filter(layer -> layer.getLayerKind().get() == LayerKind.LIBS || layer.getLayerKind().get() == LayerKind.SNAPSHOT_LIBS)
            .flatMap(layer -> layer.getFiles().getFiles().stream())
            .toList()));
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
