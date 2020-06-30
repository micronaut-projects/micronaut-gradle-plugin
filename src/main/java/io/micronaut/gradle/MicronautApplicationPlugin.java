package io.micronaut.gradle;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.*;

import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME;

/**
 * A plugin for a Micronaut application. Applies the "application" plugin.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautApplicationPlugin extends MicronautLibraryPlugin {

    public static final String CONFIGURATION_DEVELOPMENT_ONLY = "developmentOnly";

    @Override
    public void apply(Project project) {
        super.apply(project);

        Configuration developmentOnly = project
                .getConfigurations().create(CONFIGURATION_DEVELOPMENT_ONLY);


        if (Os.isFamily(Os.FAMILY_MAC)) {
            project.getDependencies().add(CONFIGURATION_DEVELOPMENT_ONLY, "io.micronaut:micronaut-runtime-osx");
        }

        project.afterEvaluate(p -> {
            final MicronautExtension ext = p.getExtensions().getByType(MicronautExtension.class);
            final String v = getMicronautVersion(p, ext);
            final DependencyHandler dependencyHandler = p.getDependencies();
            dependencyHandler.add(CONFIGURATION_DEVELOPMENT_ONLY,
                    dependencyHandler.platform("io.micronaut:micronaut-bom:" + v));
        });
        final TaskContainer tasks = project.getTasks();
        tasks.withType(JavaExec.class, javaExec -> {
            javaExec.classpath(developmentOnly);
            if (project.getGradle().getStartParameter().isContinuous()) {
                Map<String, Object> sysProps = new LinkedHashMap<>();
                sysProps.put("micronaut.io.watch.restart", true);
                sysProps.put("micronaut.io.watch.enabled", true);
                sysProps.put("micronaut.io.watch.paths", "src/main");
                javaExec.systemProperties(
                        sysProps
                );
            }
        });

        tasks.withType(ShadowJar.class, ShadowJar::mergeServiceFiles);
        File applicationLayout = new File(project.getBuildDir(), "layers");
        TaskProvider<Jar> runnerJar = tasks.register("runnerJar", Jar.class, jar -> {
            jar.dependsOn(tasks.findByName("classes"));
            SourceSetContainer sourceSets = project
                    .getExtensions().getByType(SourceSetContainer.class);

            SourceSet mainSourceSet = sourceSets
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            FileCollection dirs = mainSourceSet.getOutput().getClassesDirs();
            jar.getArchiveClassifier().set("runner");
            jar.from(dirs);
            jar.manifest(manifest -> {
                Map<String, Object> attrs = new HashMap<>(2);
                JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
                String mainClassName = javaApplication.getMainClassName();
                Configuration runtimeClasspath = project.getConfigurations()
                        .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME);

                List<String> classpath = new ArrayList<>();
                for (File file : runtimeClasspath) {
                    classpath.add("libs/" + file.getName());
                }
                classpath.add("resources/");

                attrs.put("Main-Class", mainClassName);
                attrs.put("Class-Path", String.join(" ", classpath));
                manifest.attributes(attrs);
            });
        });
        TaskProvider<Task> taskProvider = tasks.register("buildLayers", task -> {
            task.dependsOn(runnerJar);

            Configuration runtimeClasspath = project.getConfigurations()
                    .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME);
            TaskOutputs jar = runnerJar.get().getOutputs();
            FileCollection jarFiles = jar.getFiles();
            SourceSetContainer sourceSets = project
                    .getExtensions().getByType(SourceSetContainer.class);

            SourceSet mainSourceSet = sourceSets
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            Set<File> resourceDirs = mainSourceSet.getResources().getSrcDirs();

            TaskInputs inputs = task.getInputs();
            inputs.files(runtimeClasspath);
            inputs.files(jarFiles);
            inputs.files(resourceDirs);


            task.getOutputs().dir(applicationLayout);
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds application layers for use in a Docker container");

            // NOTE: Has to be an anonymous inner class otherwise incremental build does not work
            // DO NOT REPLACE WITH LAMBDA
            //noinspection Convert2Lambda
            task.doLast(new Action<Task>() {
                @Override
                public void execute(Task t) {
                    project.copy(copy ->
                            copy.from(runtimeClasspath)
                                    .into(new File(applicationLayout, "libs"))
                    );
                    project.copy(copy ->
                            copy.from(jarFiles)
                                    .into(applicationLayout)
                                    .rename(s -> "application.jar")
                    );
                    project.copy(copy ->
                            copy.from(project.files(resourceDirs))
                                    .into(new File(applicationLayout, "resources"))
                    );
                }
            });
        });

        Task assemble = tasks.findByName("assemble");
        if (assemble != null) {
            assemble.dependsOn(taskProvider);
        }
    }

    @Override
    protected String getBasePluginName() {
        return "application";
    }
}
