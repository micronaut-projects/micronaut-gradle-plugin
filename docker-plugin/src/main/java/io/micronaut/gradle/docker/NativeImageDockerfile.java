package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.DefaultVersions;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.docker.model.Layer;
import io.micronaut.gradle.docker.tasks.DockerResourceConfigDirectoryNamer;
import io.micronaut.gradle.graalvm.NativeLambdaExtension;
import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.dsl.NativeResourcesOptions;
import org.graalvm.buildtools.gradle.dsl.agent.DeprecatedAgentOptions;
import org.graalvm.buildtools.gradle.internal.BaseNativeImageOptions;
import org.graalvm.buildtools.gradle.internal.NativeImageCommandLineProvider;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.graalvm.buildtools.gradle.tasks.CreateLayerOptions;
import org.graalvm.buildtools.gradle.tasks.LayerOptions;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.micronaut.gradle.PluginsHelper.findMicronautExtension;
import static io.micronaut.gradle.docker.MicronautDockerfile.DEFAULT_WORKING_DIR;
import static io.micronaut.gradle.docker.MicronautDockerfile.applyStandardTransforms;

/**
 * Specialization of {@link Dockerfile} for building native images.
 *
 * @author gkrocher
 * @since 1.0.0
 */
public abstract class NativeImageDockerfile extends Dockerfile implements DockerBuildOptions {

    public static final String AMAZON_LINUX_BASE_IMAGE = "public.ecr.aws/amazonlinux/amazonlinux:" + DefaultVersions.AMAZONLINUX;

    private static final List<Integer> SUPPORTED_JAVA_VERSIONS = List.of(
            // keep those in descending order
            21,
            17
    );
    private static final String ARM_ARCH = "aarch64";
    private static final String X86_64_ARCH = "x64";

    private static final String GRAALVM_DOWNLOAD_BASE_URL = "https://download.oracle.com/graalvm";
    private static final String GRAALVM_DISTRIBUTION_PATH = "/%s/%s/graalvm-jdk-%s_linux-%s_bin.tar.gz";
    //Latest version of GraalVM for JDK 17 available under the GraalVM Free Terms and Conditions (GFTC) licence
    private static final String GRAALVM_FOR_JDK17 = "17.0.12";

    /**
     * @return The JDK version to use with native image. Defaults to the toolchain version, or the current Java version.
     */
    @Input
    public abstract Property<String> getJdkVersion();

    /**
     * @return The Graal architecture
     */
    @Input
    public abstract Property<String> getGraalArch();

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
    @Override
    public abstract Property<String> getBaseImage();

    /**
     * @return The arguments to pass to the native image executable when starting up in the docker container.
     */
    @Input
    @Override
    public abstract ListProperty<String> getArgs();

    @Input
    @Override
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
    @Override
    public abstract Property<String> getDefaultCommand();

    /**
     * @return URL of the Graal releases location
     */
    @Input
    @Optional
    public abstract Property<String> getGraalReleasesUrl();

    /**
     * Return the full URL of the GraalVM distribution to use. By default, it
     * uses the base URL from {@link #getGraalReleasesUrl()} and searches in a
     * location known to exist when the plugin was built.
     *
     * @return the URL of the GraalVM distribution to use
     */
    @Input
    @Optional
    public abstract Property<String> getGraalVMDistributionUrl();

    @Input
    @Override
    public abstract Property<String> getTargetWorkingDirectory();

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ProjectLayout getLayout();

    @Input
    @Optional
    public Provider<List<String>> getTweaks() {
        return getDockerfileTweaks().map(tweaks -> DockerfileEditor.fingerprintOf(getObjects(), tweaks));
    }

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

    public NativeImageDockerfile() {
        Project project = getProject();
        JavaPluginExtension javaExtension = PluginsHelper.javaPluginExtensionOf(project);
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
                        .map(String::valueOf)
        );
        String osArch = System.getProperty("os.arch");
        getGraalArch().convention(ARM_ARCH.equals(osArch) ? ARM_ARCH : X86_64_ARCH);
        getTargetWorkingDirectory().convention(DEFAULT_WORKING_DIR);
        getExposedPorts().convention(Collections.singletonList(8080));
        getGraalImage().convention(getJdkVersion().map(NativeImageDockerfile::toGraalVMBaseImageName));
        getGraalReleasesUrl().convention(GRAALVM_DOWNLOAD_BASE_URL);
        var distributionPath = getJdkVersion().zip(getGraalArch(), (jdk, arch) -> {
            if ("17".equals(jdk)) {
                getLogger().warn("You are using the latest release of GraalVM available under the GraalVM Free Terms and Conditions (GFTC) licence (" + GRAALVM_FOR_JDK17 + "). Consider upgrading to Java 21.");
                return GRAALVM_DISTRIBUTION_PATH.formatted(jdk, "archive", GRAALVM_FOR_JDK17, arch);
            }
            return GRAALVM_DISTRIBUTION_PATH.formatted(jdk, "latest", jdk, arch);
        });
        getGraalVMDistributionUrl().convention(
            getGraalReleasesUrl().zip(distributionPath, (base, path) -> base + path)
        );
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
                        public MapProperty<String, Object> getEnvironmentVariables() {
                            return delegate.getEnvironmentVariables();
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

                        @Override
                        public Property<Boolean> getQuickBuild() {
                            return delegate.getQuickBuild();
                        }

                        @Override
                        public Property<Boolean> getRichOutput() {
                            return delegate.getRichOutput();
                        }

                        @Override
                        public MapProperty<Object, List<String>> getExcludeConfig() {
                            return delegate.getExcludeConfig();
                        }

                        @Override
                        public void agent(Action<? super DeprecatedAgentOptions> spec) {
                            delegate.agent(spec);
                        }

                        @Override
                        public ListProperty<String> getExcludeConfigArgs() {
                            return delegate.getExcludeConfigArgs();
                        }

                        @Override
                        public Property<Boolean> getSharedLibrary() {
                            return delegate.getSharedLibrary();
                        }

                        @Override
                        @Optional
                        public Property<JavaLauncher> getJavaLauncher() {
                            // The native image docker file generator does NOT require
                            // GraalVM nor will it use the java launcher, so we make
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

                        @Override
                        public DeprecatedAgentOptions getAgent() {
                            return delegate.getAgent();
                        }

                        @Override
                        public Property<String> getRequiredVersion() {
                            return delegate.getRequiredVersion();
                        }

                        @Override
                        public Property<Boolean> getPgoInstrument() {
                            return delegate.getPgoInstrument();
                        }

                        @Override
                        public DirectoryProperty getPgoProfilesDirectory() {
                            return delegate.getPgoProfilesDirectory();
                        }

                        @Override
                        public DomainObjectSet<LayerOptions> getLayers() {
                            return delegate.getLayers();
                        }

                        @Override
                        public void layers(Action<? super DomainObjectSet<LayerOptions>> action) {
                            delegate.layers(action);
                        }

                        @Override
                        public void useLayer(String name) {
                            delegate.useLayer(name);
                        }

                        @Override
                        public void createLayer(Action<? super CreateLayerOptions> action) {
                            delegate.createLayer(action);
                        }
                    };
                })
        );

        //noinspection Convert2Lambda
        doLast(new Action<>() {
            @Override
            public void execute(Task task) {
                java.io.File f = getDestFile().get().getAsFile();
                getLogger().lifecycle("Dockerfile written to: {}", f.getAbsolutePath());
            }
        });
    }

    private static String toGraalVMBaseImageName(String jdkVersion) {
        return "ghcr.io/graalvm/native-image-community:" + jdkVersion + "-ol" + DefaultVersions.ORACLELINUX;
    }

    private static int toSupportedJavaVersion(int version) {
        for (Integer javaVersion : SUPPORTED_JAVA_VERSIONS) {
            if (version >= javaVersion) {
                return javaVersion;
            }
        }
        return SUPPORTED_JAVA_VERSIONS.stream().reduce((x, y) -> y).orElse(17);
    }

    @TaskAction
    @Override
    public void create() throws IOException {
        super.create();
        applyStandardTransforms(getUseCopyLink(), getObjects(), this);
        if (getDockerfileTweaks().isPresent()) {
            DockerfileEditor.apply(getObjects(), this, getDockerfileTweaks().get());
        }
    }

    // Everything done in this method MUST be lazy, so use providers as much as possible
    private void setupInstructions(List<Instruction> additionalInstructions) {
        DockerBuildStrategy buildStrategy = getBuildStrategy().get();
        var imageResolver = new BaseImageForBuildStrategyResolver(buildStrategy);
        Provider<From> baseImageProvider = getProviders().provider(() -> new From(imageResolver.get()));
        if (buildStrategy == DockerBuildStrategy.LAMBDA) {
            from(new From(imageResolver.resolve()).withStage("graalvm"));
            environmentVariable("LANG", "en_US.UTF-8");
            runCommand("dnf update -y && dnf install -y gcc glibc-devel zlib-devel libstdc++-static tar && dnf clean all && rm -rf /var/cache/dnf");
            String jdkVersion = getJdkVersion().get();
            String graalArch = getGraalArch().get();
            // https://download.oracle.com/graalvm/17/latest/graalvm-jdk-17_linux-aarch64_bin.tar.gz
            String fileName = "graalvm-jdk-" + jdkVersion + "_linux-" + graalArch + "_bin.tar.gz";
            String graalvmDistributionUrl = getGraalVMDistributionUrl().get();
            runCommand("curl -4 -L " + graalvmDistributionUrl + " -o /tmp/" + fileName);
            runCommand("tar -zxf /tmp/" + fileName + " -C /tmp && ls -d /tmp/graalvm-jdk-"+ jdkVersion + "* | grep -v \"tar.gz\" | xargs -I_ mv _ /usr/lib/graalvm");
            runCommand("rm -rf /tmp/*");
            if (toMajorVersion(jdkVersion) < 21) {
                // The GraalVM Updater was removed in GraalVM for JDK 21
                // https://github.com/oracle/graal/issues/6855
                runCommand("/usr/lib/graalvm/bin/gu install native-image");
            }
            defaultCommand("/usr/lib/graalvm/bin/native-image");
            environmentVariable("PATH", "/usr/lib/graalvm/bin:${PATH}");
            from(new From("graalvm").withStage("builder"));
            runCommand("dnf update -y && dnf install -y zip && dnf clean all");
        } else {
            from(new From(getGraalImage().get()).withStage("graalvm"));
        }

        MicronautDockerfile.setupResources(this, getLayers().get(), null);
        Property<String> executable = getObjects().property(String.class);
        executable.set("application");
        String workDir = getTargetWorkingDirectory().get();
        runCommand("mkdir " + workDir + "/config-dirs");
        getInstructions().addAll(getNativeImageOptions().map(options -> {
                    var namer = new DockerResourceConfigDirectoryNamer();
                    return options.getConfigurationFileDirectories()
                            .getFiles()
                            .stream()
                            .filter(java.io.File::exists)
                            .map(dir -> {
                                String dirName = namer.determineNameFor(dir);
                                return new RunCommandInstruction("mkdir -p " + workDir + "/config-dirs/" + dirName);
                            })
                            .toList();
                }
        ));
        getInstructions().addAll(getNativeImageOptions().map(options -> {
                    var namer = new DockerResourceConfigDirectoryNamer();
                    return options.getConfigurationFileDirectories()
                            .getFiles()
                            .stream()
                            .filter(java.io.File::exists)
                            .map(dir -> toCopyResourceDirectoryInstruction(dir, namer))
                            .toList();
                }
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
                    var newList = new ArrayList<String>(strings.size() + 1);
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
                runCommand("dnf install -y zip");
                copyFile(new CopyFile(workDir + "/application", "/function/func").withStage("builder"));
                String funcCmd = String.join(" ", getArgs().map(strings -> {
                    var newList = new ArrayList<String>(strings.size() + 1);
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
                exposePort(getExposedPorts());
                getInstructions().addAll(additionalInstructions);
                copyFile(new CopyFile(workDir + "/application", "/app/application").withStage("graalvm"));
                entryPoint(getArgs().map(strings -> {
                    var newList = new ArrayList<String>(strings.size() + 1);
                    newList.add("/app/application");
                    newList.addAll(strings);
                    return newList;
                }));
                break;
        }
    }

    private CopyFileInstruction toCopyResourceDirectoryInstruction(java.io.File resourceDirectory, DockerResourceConfigDirectoryNamer namer) {
        String relativePath = namer.determineNameFor(resourceDirectory).replace(java.io.File.separatorChar, '/');
        return new CopyFileInstruction(new CopyFile(
                "config-dirs/" + relativePath,
                getTargetWorkingDirectory().get() + "/config-dirs/" + relativePath
        ));
    }

    protected List<String> buildActualCommandLine(Provider<String> executable, DockerBuildStrategy buildStrategy, BaseImageForBuildStrategyResolver imageResolver) {
        NativeImageOptions options = newNativeImageOptions("actualDockerOptions");
        prepareNativeImageOptions(options);
        if (buildStrategy == DockerBuildStrategy.ORACLE_FUNCTION) {
            options.getMainClass().set("com.fnproject.fn.runtime.EntryPoint");
            options.getBuildArgs().add("--report-unsupported-elements-at-runtime");
            options.getBuildArgs().add("-H:PageSize=64k");
        } else if (buildStrategy == DockerBuildStrategy.LAMBDA) {
            var micronautExtension = findMicronautExtension(getProject());
            var nativeLambdaExtension = micronautExtension.getExtensions().getByType(NativeLambdaExtension.class);
            JavaApplication javaApplication = getProject().getExtensions().getByType(JavaApplication.class);
            if (!javaApplication.getMainClass().isPresent()) {
                options.getMainClass().set(nativeLambdaExtension.getLambdaRuntimeClassName());
            }
            if (!options.getMainClass().isPresent()) {
                options.getMainClass().set(nativeLambdaExtension.getLambdaRuntimeClassName());
            }
        }
        var commandLine = new ArrayList<String>();
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
                executable,
                getObjects().property(String.class),
                getObjects().property(String.class),
                getObjects().fileProperty(),
                getProviders().provider(() -> false), // in a docker container we don't use the @arg file
                getObjects().property(Integer.class).value(getJdkVersion().map(NativeImageDockerfile::toMajorVersion)),
                getProviders().provider(() -> false) // in a docker container we don't use color output
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
                    .toList();
        }
        return args;
    }

    private static Integer toMajorVersion(String version) {
        if (version.contains(".")) {
            return Integer.parseInt(version.substring(0, version.indexOf('.')));
        }
        return Integer.parseInt(version);
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
        options.getExcludeConfigArgs().set(originalOptions.flatMap(NativeImageOptions::getExcludeConfigArgs).map(this::remapExcludeConfigArgs));
        options.getRichOutput().set(false);
        var namer = new DockerResourceConfigDirectoryNamer();
        Provider<List<String>> remappedConfigDirectories = originalOptions.map(orig -> orig.getConfigurationFileDirectories()
                .getFiles()
                .stream()
                .map(f -> getTargetWorkingDirectory().get() + "/config-dirs/" + namer.determineNameFor(f))
                .toList()
        );
        options.getConfigurationFileDirectories().setFrom(
                remappedConfigDirectories
        );
        options.getClasspath().from(
                getTargetWorkingDirectory().map(d -> d + "/libs/*.jar"),
                getTargetWorkingDirectory().map(d -> d + "/resources"),
                getTargetWorkingDirectory().map(d -> d + "/application.jar")
        );
        options.getImageName().set("application");
    }

    /**
     * Exclude config args that we get reference files from the original file system,
     * but we are building within docker, and the libraries are now in a different
     * directory, so we're monkey patching the config args to point to the new location.
     *
     * @param args the original args, containing references to the original file system
     * @return the patched args, with references to the docker context
     */
    private List<String> remapExcludeConfigArgs(List<String> args) {
        return args.stream().map(arg -> {
            if (arg.startsWith("\\Q") && arg.contains(".jar")) {
                // The string ends with \Q or \E. Skip it in the search.
                int index = arg.lastIndexOf(java.io.File.separatorChar, arg.length() - 3);
                if (index > 0) {
                    // Why aren't we using `\Q` and `\E` here?
                    // Because for some reason, it doesn't when we build under
                    // docker.
                    return ".*/libs/" + arg.substring(index + 1, arg.length() - 2);
                }
            }
            return arg;
        }).toList();
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
        var additionalInstructions = new ArrayList<>(getInstructions().get());
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
        this.getExposedPorts().set(List.of(ports));
        return this;
    }

    private String getProjectFnVersion() {
        JavaVersion javaVersion = Jvm.current().getJavaVersion();
        if (javaVersion != null && javaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
            return "jre17-latest";
        }
        return "latest";
    }

    protected class BaseImageForBuildStrategyResolver {
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
                baseImage = AMAZON_LINUX_BASE_IMAGE;
            } else if (baseImage == null) {
                baseImage = "cgr.dev/chainguard/wolfi-base:latest";
            }

            return baseImage;
        }
    }

}
