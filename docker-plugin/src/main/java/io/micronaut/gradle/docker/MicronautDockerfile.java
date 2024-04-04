package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.docker.model.Layer;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
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

    public MicronautDockerfile() {
        Project project = getProject();
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a Docker File for a Micronaut application");
        ObjectFactory objects = project.getObjects();
        this.buildStrategy = objects.property(DockerBuildStrategy.class)
                                    .convention(DockerBuildStrategy.DEFAULT);
        this.baseImage = objects.property(String.class).convention("none");
        this.defaultCommand = objects.property(String.class).convention("none");
        this.args = objects.listProperty(String.class);
        this.exposedPorts = objects.listProperty(Integer.class)
                    .convention(Collections.singletonList(8080));
        this.targetWorkingDirectory = objects.property(String.class).convention(DEFAULT_WORKING_DIR);
        JavaPluginExtension javaExtension = PluginsHelper.javaPluginExtensionOf(project);
        getJdkVersion().set(javaExtension.getTargetCompatibility());
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

    @Input
    @Optional
    protected Provider<List<String>> getTweaks() {
        return getDockerfileTweaks().map(tweaks -> DockerfileEditor.fingerprintOf(getObjects(), tweaks));
    }

    @TaskAction
    @Override
    public void create() throws IOException {
        super.create();
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

    protected void setupInstructions(List<Instruction> additionalInstructions) {
        String workDir = getTargetWorkingDirectory().get();
        DockerBuildStrategy buildStrategy = this.buildStrategy.getOrElse(DockerBuildStrategy.DEFAULT);
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
                javaApplication.getMainClass().set("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
            default:
                from(new Dockerfile.From(from != null ? from : DEFAULT_BASE_IMAGE + getDockerDefaultImageJavaTag()));
                setupResources(this, getLayers().get(), null);
                exposePort(exposedPorts);
                getInstructions().addAll(additionalInstructions);
                if (getInstructions().get().stream().noneMatch(instruction -> instruction.getKeyword().equals(EntryPointInstruction.KEYWORD))) {
                    entryPoint(getArgs().map(strings -> {
                        List<String> newList = new ArrayList<>(strings.size() + 3);
                        newList.add("java");
                        newList.addAll(strings);
                        newList.add("-jar");
                        newList.add(workDir + "/application.jar");
                        return newList;
                    }));
                }
        }
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
        List<Instruction> additionalInstructions = new ArrayList<>(getInstructions().get().subList(1, getInstructions().get().size()));
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
