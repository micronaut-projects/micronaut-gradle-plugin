package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jvm.Jvm;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class MicronautDockerfile extends Dockerfile implements DockerBuildOptions {
    public static final String DEFAULT_WORKING_DIR = "/home/app";
    public static final String DEFAULT_BASE_IMAGE = "eclipse-temurin:17-jre-focal";

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

    @Input
    private final Property<String> targetWorkingDirectory;

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
    }

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
        if (getDockerfileTweaks().isPresent()) {
            DockerfileEditor.apply(getObjects(), this, getDockerfileTweaks().get());
        }
        System.out.println("Dockerfile written to: " + getDestFile().get().getAsFile().getAbsolutePath());
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
                from(new Dockerfile.From(from != null ? from : "fnproject/fn-java-fdk:" + getProjectFnVersion()));
                workingDir("/function");
                runCommand("mkdir -p /function/app/resources");
                copyFile("layers/libs/*.jar", "/function/app/");
                copyFile("layers/classes", "/function/app/classes");
                copyFile("layers/resources", "/function/app/resources");
                copyFile("layers/application.jar", "/function/app/");
                String cmd = this.defaultCommand.get();
                if ("none".equals(cmd)) {
                    super.defaultCommand("io.micronaut.oraclecloud.function.http.HttpFunction::handleRequest");
                } else {
                    super.defaultCommand(cmd);
                }
                break;
            case LAMBDA:
                javaApplication.getMainClass().set("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
            default:
                from(new Dockerfile.From(from != null ? from : DEFAULT_BASE_IMAGE));
                setupResources(this);
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

    private String getProjectFnVersion() {
        JavaVersion javaVersion = Jvm.current().getJavaVersion();
        if (javaVersion != null && javaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
            return "jre17-latest";
        }
        return "latest";
    }

    static void setupResources(Dockerfile task) {
        String workDir = DEFAULT_WORKING_DIR;
        if (task instanceof DockerBuildOptions dbo) {
            workDir = dbo.getTargetWorkingDirectory().get();
        }
        task.workingDir(workDir);
        task.copyFile("layers/libs", workDir + "/libs");
        task.copyFile("layers/classes", workDir + "/classes");
        task.copyFile("layers/resources", workDir + "/resources");
        task.copyFile("layers/application.jar", workDir + "/application.jar");
    }
}
