package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.AgentConfiguration;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.dsl.NativeResourcesOptions;
import org.graalvm.buildtools.gradle.internal.BaseNativeImageOptions;
import org.graalvm.buildtools.gradle.internal.NativeImageCommandLineProvider;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.micronaut.gradle.docker.MicronautDockerfile.DEFAULT_WORKING_DIR;

/**
 * Specialization of {@link Dockerfile} for building native images.
 *
 * @author gkrocher
 * @since 1.0.0
 */
public abstract class NativeImageDockerfile extends Dockerfile implements DockerBuildOptions {

    /**
     * Main class for Provided runtime and Application Type: Micronaut Application.
     */
    public static final String MICRONAUT_LAMBDA_RUNTIME = "io.micronaut.function.aws.runtime.MicronautLambdaRuntime";

    private static final List<Integer> SUPPORTED_JAVA_VERSIONS = Collections.unmodifiableList(
            // keep those in descending order
            Arrays.asList(17, 11)
    );


    /**
     * @return The JDK version to use with native image. Defaults to the toolchain version, or the current Java version.
     */
    @Input
    public abstract Property<String> getJdkVersion();

    /**
     * @return The Graal version to use.
     */
    @Input
    public abstract Property<String> getGraalVersion();

    /**
     * @return the GraalVM docker image to use
     */
    @Input
    public abstract Property<String> getGraalImage();

    /**
     * @return The base image to use
     */
    @Input
    @Optional
    public abstract Property<String> getBaseImage();

    /**
     * @return The arguments to pass to the native image executable when starting up in the docker container.
     */
    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    public abstract ListProperty<Integer> getExposedPorts();

    /**
     * @return Whether a Graal SDK is required (defaults to 'true').
     */
    @Input
    public abstract Property<Boolean> getRequireGraalSdk();

    /**
     * @return The build strategy
     */
    @Input
    public abstract Property<DockerBuildStrategy> getBuildStrategy();

    @Nested
    public abstract Property<NativeImageOptions> getNativeImageOptions();

    @Inject
    public abstract JavaToolchainService getJavaToolchainService();

    @Input
    @Optional
    public abstract Property<String> getDefaultCommand();

    @Input
    @Override
    public abstract Property<String> getTargetWorkingDirectory();

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ProjectLayout getLayout();

    @Inject
    protected abstract FileOperations getFileOperations();

    public NativeImageDockerfile() {
        Project project = getProject();
        JavaPluginExtension javaExtension = (JavaPluginExtension) project.getExtensions().getByName("java");
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a Docker File for Native Image");
        getDestFile().set(project.getLayout().getBuildDirectory().file("docker/DockerfileNative"));
        getBuildStrategy().convention(DockerBuildStrategy.DEFAULT);
        getRequireGraalSdk().convention(true);
        getJdkVersion().convention(
                javaExtension.getToolchain()
                        .getLanguageVersion()
                        .map(JavaLanguageVersion::asInt)
                        .orElse(project.getProviders().provider(() -> Integer.valueOf(Jvm.current().getJavaVersion().getMajorVersion())))
                        .map(NativeImageDockerfile::toSupportedJavaVersion)
                        .map(v -> "java" + v)
        );
        getGraalVersion().convention("21.3.0");
        getTargetWorkingDirectory().convention(DEFAULT_WORKING_DIR);
        getGraalImage().convention(getGraalVersion().zip(getJdkVersion(), NativeImageDockerfile::toGraalVMBaseImageName));
        getNativeImageOptions().convention(project
                .getTasks()
                .named(NativeImagePlugin.NATIVE_COMPILE_TASK_NAME, BuildNativeImageTask.class)
                .map(bniT -> {
                    NativeImageOptions delegate = bniT.getOptions().get();
                    return new NativeImageOptions() {
                        @Override
                        public String getName() {
                            return delegate.getName();
                        }

                        @Override
                        public Property<String> getImageName() {
                            return delegate.getImageName();
                        }

                        @Override
                        public Property<String> getMainClass() {
                            return delegate.getMainClass();
                        }

                        @Override
                        public ListProperty<String> getBuildArgs() {
                            return delegate.getBuildArgs();
                        }

                        @Override
                        public MapProperty<String, Object> getSystemProperties() {
                            return delegate.getSystemProperties();
                        }

                        @Override
                        public ConfigurableFileCollection getClasspath() {
                            return delegate.getClasspath();
                        }

                        @Override
                        public ListProperty<String> getJvmArgs() {
                            return delegate.getJvmArgs();
                        }

                        @Override
                        public ListProperty<String> getRuntimeArgs() {
                            return delegate.getRuntimeArgs();
                        }

                        @Override
                        public Property<Boolean> getDebug() {
                            return delegate.getDebug();
                        }

                        @Override
                        public Property<Boolean> getFallback() {
                            return delegate.getFallback();
                        }

                        @Override
                        public Property<Boolean> getVerbose() {
                            return delegate.getVerbose();
                        }

                        /**
                         * Configures the GraalVM agent options.
                         *
                         * @param spec the agent configuration.
                         */
                        @Override
                        public void agent(Action<? super AgentConfiguration> spec) {
                            delegate.agent(spec);
                        }

                        /**
                         * Returns the GraalVM agent configuration.
                         *
                         * @return the configuration.
                         */
                        @Override
                        public AgentConfiguration getAgent() {
                            return delegate.getAgent();
                        }

                        @Override
                        public Property<Boolean> getSharedLibrary() {
                            return delegate.getSharedLibrary();
                        }

                        @Override
                        @Optional
                        public Property<JavaLauncher> getJavaLauncher() {
                            // The native image docker file generator does NOT require
                            // GraalVM nor will it use the java launcher so we make
                            // the property optional
                            return getObjects().property(JavaLauncher.class);
                        }

                        @Override
                        public ConfigurableFileCollection getConfigurationFileDirectories() {
                            return delegate.getConfigurationFileDirectories();
                        }

                        @Override
                        public NativeResourcesOptions getResources() {
                            return delegate.getResources();
                        }

                        @Override
                        public void resources(Action<? super NativeResourcesOptions> spec) {
                            delegate.resources(spec);
                        }

                        @Override
                        public NativeImageOptions buildArgs(Object... buildArgs) {
                            delegate.buildArgs(buildArgs);
                            return this;
                        }

                        @Override
                        public NativeImageOptions buildArgs(Iterable<?> buildArgs) {
                            delegate.buildArgs(buildArgs);
                            return this;
                        }

                        @Override
                        public NativeImageOptions systemProperties(Map<String, ?> properties) {
                            delegate.systemProperties(properties);
                            return this;
                        }

                        @Override
                        public NativeImageOptions systemProperty(String name, Object value) {
                            delegate.systemProperty(name, value);
                            return this;
                        }

                        @Override
                        public NativeImageOptions classpath(Object... paths) {
                            delegate.classpath(paths);
                            return this;
                        }

                        @Override
                        public NativeImageOptions jvmArgs(Object... arguments) {
                            delegate.jvmArgs(arguments);
                            return this;
                        }

                        @Override
                        public NativeImageOptions jvmArgs(Iterable<?> arguments) {
                            delegate.jvmArgs(arguments);
                            return this;
                        }

                        @Override
                        public NativeImageOptions runtimeArgs(Object... arguments) {
                            delegate.runtimeArgs(arguments);
                            return this;
                        }

                        @Override
                        public NativeImageOptions runtimeArgs(Iterable<?> arguments) {
                            delegate.runtimeArgs(arguments);
                            return this;
                        }

                        @Override
                        public Property<Boolean> getUseFatJar() {
                            return delegate.getUseFatJar();
                        }
                    };
                })
        );

        //noinspection Convert2Lambda
        doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                java.io.File f = NativeImageDockerfile.this.getDestFile().get().getAsFile();
                System.out.println("Dockerfile written to: " + f.getAbsolutePath());
            }
        });
    }

    private static String toGraalVMBaseImageName(String graalVersion, String jdkVersion) {
        return "ghcr.io/graalvm/native-image:" + jdkVersion + '-' + graalVersion;
    }

    private static int toSupportedJavaVersion(int version) {
        for (Integer javaVersion : SUPPORTED_JAVA_VERSIONS) {
            if (version >= javaVersion) {
                return javaVersion;
            }
        }
        return SUPPORTED_JAVA_VERSIONS.stream().reduce((x, y) -> y).orElse(8);
    }

    @TaskAction
    @Override
    public void create() {
        super.create();
    }

    private Provider<Directory> getConfigurationFilesDirectory() {
        return getLayout().getBuildDirectory().dir("docker/config-dirs");
    }

    // Everything done in this method MUST be lazy, so use providers as much as possible
    private void setupInstructions(List<Instruction> additionalInstructions) {
        DockerBuildStrategy buildStrategy = getBuildStrategy().get();
        BaseImageForBuildStrategyResolver imageResolver = new BaseImageForBuildStrategyResolver(buildStrategy);
        Provider<From> baseImageProvider = getProviders().provider(() -> new From(imageResolver.get()));
        if (buildStrategy == DockerBuildStrategy.LAMBDA) {
            from(new From(imageResolver.resolve()).withStage("graalvm"));
            environmentVariable("LANG", "en_US.UTF-8");
            runCommand("yum install -y gcc gcc-c++ libc6-dev zlib1g-dev curl bash zlib zlib-devel zlib-static zip tar gzip");
            String jdkVersion = getJdkVersion().get();
            String graalVersion = getGraalVersion().get();
            String fileName = "graalvm-ce-" + jdkVersion + "-linux-amd64-" + graalVersion + ".tar.gz";
            runCommand("curl -4 -L https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-" + graalVersion + "/" + fileName + " -o /tmp/" + fileName);
            runCommand("tar -zxf /tmp/" + fileName + " -C /tmp && mv /tmp/graalvm-ce-" + jdkVersion + "-" + graalVersion + " /usr/lib/graalvm");
            runCommand("rm -rf /tmp/*");
            runCommand("/usr/lib/graalvm/bin/gu install native-image");
            defaultCommand("/usr/lib/graalvm/bin/native-image");
            environmentVariable("PATH", "/usr/lib/graalvm/bin:${PATH}");
            from(new From("graalvm").withStage("builder"));
        } else {
            from(new From(getGraalImage().get()).withStage("graalvm"));
        }

        MicronautDockerfile.setupResources(this);
        Property<String> executable = getObjects().property(String.class);
        executable.set("application");
        String workDir = getTargetWorkingDirectory().get();
        runCommand("mkdir " + workDir + "/config-dirs");
        getInstructions().addAll(getNativeImageOptions().map(options ->
                options.getConfigurationFileDirectories()
                        .getFiles()
                        .stream()
                        .map(this::toCopyResourceDirectoryInstruction)
                        .collect(Collectors.toList())
        ));
        runCommand(getProviders().provider(() -> String.join(" ", buildActualCommandLine(executable, buildStrategy, imageResolver))));
        switch (buildStrategy) {
            case ORACLE_FUNCTION:
                from(new From("fnproject/fn-java-fdk:" + getProjectFnVersion()).withStage("fnfdk"));
                from(baseImageProvider);
                workingDir("/function");
                copyFile(new CopyFile(workDir + "/application", "/function/func").withStage("graalvm"));
                copyFile(new CopyFile("/function/runtime/lib/*", ".").withStage("fnfdk"));
                entryPoint(getArgs().map(strings -> {
                    List<String> newList = new ArrayList<>(strings.size() + 1);
                    newList.add("./func");
                    newList.addAll(strings);
                    newList.add("-Djava.library.path=/function");
                    return newList;
                }));
                if (getDefaultCommand().isPresent()) {
                    defaultCommand(getDefaultCommand().get());
                } else {
                    defaultCommand("io.micronaut.oraclecloud.function.http.HttpFunction::handleRequest");
                }
                break;
            case LAMBDA:
                from(baseImageProvider);
                workingDir("/function");
                runCommand("yum install -y zip");
                copyFile(new CopyFile(workDir + "/application", "/function/func").withStage("builder"));
                String funcCmd = String.join(" ", getArgs().map(strings -> {
                    List<String> newList = new ArrayList<>(strings.size() + 1);
                    newList.add("./func");
                    newList.addAll(strings);
                    newList.add("-Djava.library.path=$(pwd)");
                    return newList;
                }).get());
                runCommand("echo \"#!/bin/sh\" >> bootstrap && echo \"set -euo pipefail\" >> bootstrap && echo \"" + funcCmd + "\" >> bootstrap");
                runCommand("chmod 777 bootstrap");
                runCommand("chmod 777 func");
                runCommand("zip -j function.zip bootstrap func");
                getInstructions().addAll(additionalInstructions);
                entryPoint("/function/func");
                break;
            default:
                from(baseImageProvider);
                // mandatory dependency for alpine-glibc docker images
                runCommand(getProviders().provider(() -> {
                    if (baseImageProvider.get().getImage().contains("alpine-glibc")) {
                        return "apk update && apk add libstdc++";
                    }
                    return "";
                }));
                exposePort(getExposedPorts());
                getInstructions().addAll(additionalInstructions);
                copyFile(new CopyFile(workDir + "/application", "/app/application").withStage("graalvm"));
                entryPoint(getArgs().map(strings -> {
                    List<String> newList = new ArrayList<>(strings.size() + 1);
                    newList.add("/app/application");
                    newList.addAll(strings);
                    return newList;
                }));
                break;
        }
    }

    private CopyFileInstruction toCopyResourceDirectoryInstruction(java.io.File resourceDirectory) {
        return new CopyFileInstruction(new CopyFile("config-dirs/" + resourceDirectory.getName(), getTargetWorkingDirectory().get() + "/config-dirs/" + resourceDirectory.getName()));
    }

    protected List<String> buildActualCommandLine(Provider<String> executable, DockerBuildStrategy buildStrategy, BaseImageForBuildStrategyResolver imageResolver) {
        NativeImageOptions options = newNativeImageOptions("actualDockerOptions");
        prepareNativeImageOptions(options);
        if (buildStrategy == DockerBuildStrategy.ORACLE_FUNCTION) {
            options.getMainClass().set("com.fnproject.fn.runtime.EntryPoint");
            options.getBuildArgs().add("--report-unsupported-elements-at-runtime");
        } else if (buildStrategy == DockerBuildStrategy.LAMBDA) {
            JavaApplication javaApplication = getProject().getExtensions().getByType(JavaApplication.class);
            if (!javaApplication.getMainClass().isPresent()) {
                options.getMainClass().set(MICRONAUT_LAMBDA_RUNTIME);
            }
            if (!options.getMainClass().isPresent()) {
                options.getMainClass().set(MICRONAUT_LAMBDA_RUNTIME);
            }
        }
        List<String> commandLine = new ArrayList<>();
        commandLine.add("native-image");
        List<String> args = buildNativeImageCommandLineArgs(executable, options);
        commandLine.addAll(args);

        String baseImage = imageResolver.get();

        // add --static if image is scratch
        if (baseImage.equalsIgnoreCase("scratch") && !commandLine.contains("--static")) {
            commandLine.add("--static");
        }

        // to build a "mostly" static native-image if image when using distroless
        if (baseImage.contains("distroless") && !commandLine.contains("-H:+StaticExecutableWithDynamicLibC")) {
            commandLine.add("-H:+StaticExecutableWithDynamicLibC");
        }
        return commandLine;
    }

    private List<String> buildNativeImageCommandLineArgs(Provider<String> executable, NativeImageOptions options) {
        List<String> args = new NativeImageCommandLineProvider(
                getProviders().provider(() -> options),
                getProviders().provider(() -> false),
                executable,
                getObjects().property(String.class),
                getObjects().fileProperty(),
                getProviders().provider(() -> false) // in a docker container we don't use the @arg file
        ).asArguments();
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // This is a dirty workaround for https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/358
            String current = getLayout().getProjectDirectory().dir(".").getAsFile().toPath().toAbsolutePath().toString();
            args = args.stream()
                    .map(arg -> {
                        if (arg.contains(current)) {
                            return arg.replace(current, "")
                                    .replace(java.io.File.separatorChar, '/')
                                    .replace(";", ":");
                        }
                        return arg;
                    })
                    .collect(Collectors.toList());
        }
        return args;
    }

    /**
     * Builds a new concrete list of native image options from the configuration
     * options. We don't use the actual options because we don't want to mutate
     * them.
     */
    private void prepareNativeImageOptions(NativeImageOptions options) {
        Property<NativeImageOptions> originalOptions = getNativeImageOptions();
        options.getBuildArgs().set(originalOptions.flatMap(NativeImageOptions::getBuildArgs));
        options.getJvmArgs().set(originalOptions.flatMap(NativeImageOptions::getJvmArgs));
        options.getMainClass().set(originalOptions.flatMap(NativeImageOptions::getMainClass));
        options.getVerbose().set(originalOptions.flatMap(NativeImageOptions::getVerbose));
        options.getFallback().set(originalOptions.flatMap(NativeImageOptions::getFallback));
        options.getSystemProperties().set(originalOptions.flatMap(NativeImageOptions::getSystemProperties));
        Provider<List<String>> remappedConfigDirectories = originalOptions.map(orig -> orig.getConfigurationFileDirectories()
                .getFiles()
                .stream()
                .map(f -> getTargetWorkingDirectory().get() + "/config-dirs/" + f.getName())
                .collect(Collectors.toList())
        );
        options.getConfigurationFileDirectories().setFrom(
                remappedConfigDirectories
        );
        options.getClasspath().from(getTargetWorkingDirectory().map(d -> d + "/libs/*.jar"), getTargetWorkingDirectory().map(d -> d + "/resources:" + d + "/application.jar"));
        options.getImageName().set("application");
    }

    private NativeImageOptions newNativeImageOptions(String name) {
        return getObjects().newInstance(BaseNativeImageOptions.class,
                name,
                getObjects(),
                getProviders(),
                getJavaToolchainService(),
                "application");
    }

    /**
     * This is executed post project evaluation
     */
    void setupNativeImageTaskPostEvaluate() {
        List<Instruction> additionalInstructions = new ArrayList<>(getInstructions().get());
        // Reset the instructions to empty
        getInstructions().set(new ArrayList<>());
        setupInstructions(additionalInstructions);
    }

    /**
     * Adds additional args to pass to the native image executable.
     *
     * @param args The args
     * @return This instance.
     */
    @Override
    public NativeImageDockerfile args(String... args) {
        this.getArgs().addAll(args);
        return this;
    }

    @Override
    public NativeImageDockerfile baseImage(String imageName) {
        if (imageName != null) {
            this.getBaseImage().set(imageName);
        }
        return this;
    }

    @Override
    public DockerBuildOptions exportPorts(Integer... ports) {
        this.getExposedPorts().set(Arrays.asList(ports));
        return this;
    }

    private String getProjectFnVersion() {
        JavaVersion javaVersion = Jvm.current().getJavaVersion();
        if (javaVersion != null && javaVersion.isJava11Compatible()) {
            return "jre11-latest";
        }
        return "latest";
    }

    private class BaseImageForBuildStrategyResolver {
        private final DockerBuildStrategy strategy;
        private String resolved;

        private BaseImageForBuildStrategyResolver(DockerBuildStrategy strategy) {
            this.strategy = strategy;
        }

        public String get() {
            if (resolved == null) {
                resolved = resolve();
            }
            return resolved;
        }

        private String resolve() {
            String baseImage = getBaseImage().getOrNull();

            if (strategy == DockerBuildStrategy.LAMBDA && baseImage == null) {
                baseImage = "amazonlinux:latest";
            } else if (baseImage == null) {
                baseImage = "frolvlad/alpine-glibc:alpine-3.12";
            }

            return baseImage;
        }
    }

}
