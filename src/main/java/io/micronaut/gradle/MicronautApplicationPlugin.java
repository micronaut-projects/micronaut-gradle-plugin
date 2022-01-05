package io.micronaut.gradle;

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin;
import io.micronaut.gradle.docker.MicronautDockerPlugin;
import io.micronaut.gradle.graalvm.GraalUtil;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.gradle.MicronautComponentPlugin.resolveMicronautPlatform;

/**
 * A plugin for a Micronaut application. Applies the "application" plugin.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautApplicationPlugin extends MicronautLibraryPlugin {

    public static final String CONFIGURATION_DEVELOPMENT_ONLY = "developmentOnly";
    // This flag is used for testing purposes only
    public static final String INTERNAL_CONTINUOUS_FLAG = "io.micronaut.internal.gradle.continuous";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ApplicationPlugin.class);
        super.apply(project);

        // create a configuration used for dependencies that are only used for development
        Configuration developmentOnly = project
                .getConfigurations().create(CONFIGURATION_DEVELOPMENT_ONLY);


        // added to ensure file watch works more efficiently on OS X
        if (Os.isFamily(Os.FAMILY_MAC)) {
            project.getDependencies().add(CONFIGURATION_DEVELOPMENT_ONLY, "io.micronaut:micronaut-runtime-osx");
        }

        project.afterEvaluate(p -> {
            final MicronautExtension ext = p.getExtensions().getByType(MicronautExtension.class);
            final String v = PluginsHelper.findMicronautVersion(p, ext);
            final DependencyHandler dependencyHandler = p.getDependencies();
            configureLogging(p, dependencyHandler);
            Dependency platform = resolveMicronautPlatform(dependencyHandler, v);
            dependencyHandler.add(CONFIGURATION_DEVELOPMENT_ONLY, platform);

            MicronautRuntime micronautRuntime = resolveRuntime(p);
            micronautRuntime.getDependencies().forEach((scope, dependencies) -> {
                for (String dependency : dependencies) {
                    dependencyHandler.add(scope, dependency);
                }
            });
            if (micronautRuntime == MicronautRuntime.GOOGLE_FUNCTION) {
                String invokerConfig = "invoker";
                Configuration ic = project.getConfigurations().create(invokerConfig);
                dependencyHandler.add(invokerConfig, "com.google.cloud.functions.invoker:java-function-invoker:1.0.0-beta2");

                // reconfigure the run task to use Google cloud invoker
                TaskContainer taskContainer = project.getTasks();
                taskContainer.register("runFunction", JavaExec.class, run -> {
                    run.dependsOn(taskContainer.findByName("processResources"), taskContainer.findByName("classes"));
                    run.setMain("com.google.cloud.functions.invoker.runner.Invoker");
                    run.setClasspath(ic);
                    run.setArgs(Arrays.asList(
                            "--target", "io.micronaut.gcp.function.http.HttpFunction",
                            "--port", 8080
                    ));
                    run.doFirst(t -> {
                        JavaPluginConvention plugin = project.getConvention().getPlugin(JavaPluginConvention.class);
                        SourceSet sourceSet = plugin.getSourceSets().getByName("main");
                        SourceSetOutput output = sourceSet.getOutput();
                        String runtimeClasspath = project.files(project.getConfigurations().getByName("runtimeClasspath"),
                                output
                        ).getAsPath();
                        ((JavaExec) t).args("--classpath",
                                runtimeClasspath
                        );
                    });
                });

                PluginContainer plugins = project.getPlugins();
                // Google Cloud Function requires shadow packaging
                if (!plugins.hasPlugin(ShadowPlugin.class)) {
                    plugins.apply(ShadowPlugin.class);
                }
            }
            PluginContainer plugins = project.getPlugins();
            if (plugins.hasPlugin(ShadowPlugin.class)) {
                JavaApplication javaApplication = project
                        .getExtensions().findByType(JavaApplication.class);
                if (javaApplication != null) {
                    Property<String> mainClass = javaApplication.getMainClass();
                    if (mainClass.isPresent()) {
                        project.setProperty("mainClassName", mainClass.get());
                    }
                }
            }
        });
        
        new MicronautDockerPlugin().apply(project);
        final TaskContainer tasks = project.getTasks();
        tasks.withType(JavaExec.class).configureEach(javaExec -> {
            if (javaExec.getName().equals("run")) {
                javaExec.jvmArgs(
                        "-Dcom.sun.management.jmxremote"
                );
                if (!GraalUtil.isGraalJVM()) {
                    // graal doesn't support this
                    javaExec.jvmArgs("-XX:TieredStopAtLevel=1");
                }
            }
            javaExec.classpath(developmentOnly);

            // If -t (continuous mode) is enabled feed parameters to the JVM
            // that allows it to shutdown on resources changes so a rebuild
            // can apply a restart to the application
            if (project.getGradle().getStartParameter().isContinuous() || Boolean.getBoolean(INTERNAL_CONTINUOUS_FLAG)) {
                SourceSetContainer sourceSets = project.getConvention()
                        .getPlugin(JavaPluginConvention.class)
                        .getSourceSets();
                SourceSet sourceSet = sourceSets.findByName("main");
                if (sourceSet != null) {
                    Map<String, Object> sysProps = new LinkedHashMap<>();
                    sysProps.put("micronaut.io.watch.restart", true);
                    sysProps.put("micronaut.io.watch.enabled", true);
                    javaExec.doFirst(workaroundEagerSystemProps -> {
                        String watchPaths = sourceSet
                                .getAllSource()
                                .getSrcDirs()
                                .stream()
                                .map(File::getPath)
                                .collect(Collectors.joining(","));
                        javaExec.systemProperty("micronaut.io.watch.paths", watchPaths);
                    });
                    javaExec.systemProperties(
                            sysProps
                    );
                }
            }
        });
    }

    private void configureLogging(Project p, DependencyHandler dependencyHandler) {
        SourceSetContainer sourceSets = p.getConvention().getPlugin(JavaPluginConvention.class)
                .getSourceSets();
        SourceSet sourceSet = sourceSets.findByName("main");
        if (sourceSet != null) {
            SourceDirectorySet resources = sourceSet.getResources();
            Set<File> srcDirs = resources.getSrcDirs();
            for (File srcDir : srcDirs) {
                if (new File(srcDir, "logback.xml").exists()) {
                    dependencyHandler.add(
                            sourceSet.getRuntimeOnlyConfigurationName(),
                            "ch.qos.logback:logback-classic"
                    );
                    break;
                } else if (new File(srcDir, "simplelogger.properties").exists()) {
                    dependencyHandler.add(
                            sourceSet.getRuntimeOnlyConfigurationName(),
                            "org.slf4j:slf4j-simple"
                    );
                    break;
                }
            }
        }
    }

    public static MicronautRuntime resolveRuntime(Project p) {
        MicronautExtension ext = p.getExtensions().findByType(MicronautExtension.class);
        Object o = p.findProperty("micronaut.runtime");

        MicronautRuntime micronautRuntime;
        if (o != null) {
            micronautRuntime = MicronautRuntime.valueOf(o.toString().toUpperCase(Locale.ENGLISH));
        } else if (ext == null) {
            micronautRuntime = MicronautRuntime.NONE;
        } else {
            micronautRuntime = ext.getRuntime().getOrElse(MicronautRuntime.NONE);
        }
        return micronautRuntime;
    }
}
