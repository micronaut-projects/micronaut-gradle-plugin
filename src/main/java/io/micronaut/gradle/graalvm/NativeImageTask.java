package io.micronaut.gradle.graalvm;

import org.apache.groovy.util.Maps;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.AbstractExecTask;
import org.gradle.api.tasks.OutputFile;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.*;
import java.util.function.BooleanSupplier;

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
    private final Property<Boolean> isVerbose;
    private final Property<Boolean> isServerBuild;
    private final Map<BooleanSupplier, String> booleanCmds;

    /**
     * Default constructor.
     */
    public NativeImageTask() {
        super(NativeImageTask.class);
        setExecutable("native-image");
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
        this.isVerbose = objectFactory.property(Boolean.class).convention(false);
        this.isServerBuild = objectFactory.property(Boolean.class).convention(false);
        booleanCmds = Maps.of(
                isDebug::get, "-H:GenerateDebugInfo=1",
                isVerbose::get,  "--verbose",
                () -> !isServerBuild.get(), "--no-server"
        );

    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @OutputFile
    public File getNativeImageOutput() {
        return new File(getWorkingDir(), imageName.get());
    }

    @Override
    protected void exec() {
        // set the classpath
        FileCollection cp = getClasspath();
        if (cp != null) {
            String classpath = cp.getAsPath();
            if (classpath.length() > 0) {
                args("-cp", classpath);
            }
        }

        // set the main class
        String main = getMain().get();
        args("-H:Class=" + main);

        // set the image name
        String imageName = getImageName().get();
        args("-H:Name=" + imageName);

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
        super.exec();
        System.out.println("Native Image written to: " + getNativeImageOutput());
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

    public Property<Boolean> debug() {
        return isDebug;
    }

    public Property<Boolean> verbose() {
        return isVerbose;
    }

    public Property<Boolean> serverBuild() {
        return isServerBuild;
    }
}
