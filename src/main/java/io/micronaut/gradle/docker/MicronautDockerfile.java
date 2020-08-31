package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.MicronautRuntime;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jvm.Jvm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MicronautDockerfile extends Dockerfile implements DockerBuildOptions  {

    @Input
    private final Property<String> baseImage;
    @Input
    private final ListProperty<String> args;
    @Input
    private final ListProperty<Integer> exposedPorts;
    private final Property<MicronautRuntime> micronautRuntime;


    public MicronautDockerfile() {
        Project project = getProject();
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a Docker File for a Micronaut application");
        ObjectFactory objects = project.getObjects();
        this.micronautRuntime = objects.property(MicronautRuntime.class);
        this.baseImage = objects.property(String.class).convention("none");
        this.args = objects.listProperty(String.class);
        this.exposedPorts = objects.listProperty(Integer.class)
                    .convention(Collections.singletonList(8080));
    }

    @Override
    @TaskAction
    public void create() {
        MicronautRuntime micronautRuntime = this.micronautRuntime.getOrElse(MicronautRuntime.NONE);
        String from = getBaseImage().getOrNull();
        if ("none".equalsIgnoreCase(from)) {
            from = null;
        }
        switch (micronautRuntime) {
            case ORACLE_FUNCTION:
                from(new Dockerfile.From(from != null ? from : "fnproject/fn-java-fdk:" + getProjectFnVersion()));
                workingDir("/function");
                copyFile("build/layers/libs/*.jar", "/function/app/");
                copyFile("build/layers/resources/*", "/function/app/");
                copyFile("build/layers/application.jar", "/function/app/");
                defaultCommand("io.micronaut.oraclecloud.function.http.HttpFunction::handleRequest");
            break;
            case LAMBDA:
                // TODO
            default:
                from(new Dockerfile.From(from != null ? from : "openjdk:14-alpine"));
                setupResources(this);
                exposePort(exposedPorts);
                entryPoint(getArgs().map(strings -> {
                    List<String> newList = new ArrayList<>(strings.size() + 3);
                    newList.add("java");
                    newList.addAll(strings);
                    newList.add("-jar");
                    newList.add("/home/app/application.jar");
                    return newList;
                }));
        }
        super.create();
    }

    /**
     * @return The micronaut runtime.
     */
    @Input
    public Property<MicronautRuntime> getMicronautRuntime() {
        return micronautRuntime;
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
        task.copyFile("build/layers/libs", "/home/app/libs");
        task.copyFile("build/layers/resources", "/home/app/resources");
        task.copyFile("build/layers/application.jar", "/home/app/application.jar");
    }
}
