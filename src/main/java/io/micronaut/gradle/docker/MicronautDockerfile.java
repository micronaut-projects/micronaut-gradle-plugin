package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.internal.jvm.Jvm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MicronautDockerfile extends Dockerfile implements DockerBuildOptions {

    @Input
    private final Property<String> baseImage;
    @Input
    private final ListProperty<String> args;
    @Input
    private final ListProperty<Integer> exposedPorts;
    @Input
    private final Property<DockerBuildStrategy> buildStrategy;
    @Input
    private final Property<String> defaultCommand;


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

        //noinspection Convert2Lambda
        doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                java.io.File f = MicronautDockerfile.this.getDestFile().get().getAsFile();
                System.out.println("Dockerfile written to: " + f.getAbsolutePath());
            }
        });
    }

    @Override
    public Property<String> getDefaultCommand() {
        return defaultCommand;
    }

    private void setupInstructions(List<Instruction> additionalInstructions) {
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
                copyFile("layers/resources/*", "/function/app/resources/");
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
                from(new Dockerfile.From(from != null ? from : "openjdk:16-alpine"));
                setupResources(this);
                exposePort(exposedPorts);
                getInstructions().addAll(additionalInstructions);
                if (getInstructions().get().stream().noneMatch(instruction -> instruction.getKeyword().equals(EntryPointInstruction.KEYWORD))) {
                    entryPoint(getArgs().map(strings -> {
                        List<String> newList = new ArrayList<>(strings.size() + 3);
                        newList.add("java");
                        newList.addAll(strings);
                        newList.add("-jar");
                        newList.add("/home/app/application.jar");
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
    void setupDockerfileInstructions() {
        from("placeholder");
    }

    /**
     * This is executed post project evaluation
     */
    void setupTaskPostEvaluate() {
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
        if (javaVersion != null && javaVersion.isJava11Compatible()) {
            return "jre11-latest";
        }
        return "latest";
    }

    static void setupResources(Dockerfile task) {
        task.workingDir("/home/app");
        task.copyFile("layers/libs", "/home/app/libs");
        task.copyFile("layers/resources", "/home/app/resources");
        task.copyFile("layers/application.jar", "/home/app/application.jar");
    }
}
