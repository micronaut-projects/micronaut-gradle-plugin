package io.micronaut.gradle.graalvm;

import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.MicronautRuntime;
import io.micronaut.gradle.PluginsHelper;
import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Support for building GraalVM native images.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.0.0
 */
public class MicronautGraalPlugin implements Plugin<Project> {

    public static final String RICH_OUTPUT_PROPERTY = "io.micronaut.graalvm.rich.output";

    private static final Set<String> SOURCE_SETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("main", "test")));
    private static final List<String> GRAALVM_MODULE_EXPORTS = Collections.unmodifiableList(Arrays.asList(
            "org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk",
            "org.graalvm.nativeimage.builder/com.oracle.svm.core.configure",
            "org.graalvm.sdk/org.graalvm.nativeimage.impl"
    ));

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeImagePlugin.class);
        workaroundForResourcesDirectoryMissing(project);
        project.getPluginManager().withPlugin("io.micronaut.minimal.library", plugin -> {
            MicronautExtension extension = project.getExtensions().findByType(MicronautExtension.class);
            configureAnnotationProcessing(project, extension);
        });
        project.getPluginManager().withPlugin("io.micronaut.minimal.application", plugin -> {
            MicronautExtension extension = project.getExtensions().findByType(MicronautExtension.class);
            configureAnnotationProcessing(project, extension);
        });
        GraalVMExtension graal = project.getExtensions().findByType(GraalVMExtension.class);
        graal.getBinaries().configureEach(options ->
                {
                    options.resources(rsrc -> rsrc.autodetection(inf -> {
                        inf.getEnabled().convention(true);
                        inf.getIgnoreExistingResourcesConfigFile().convention(true);
                        inf.getRestrictToProjectDependencies().convention(true);
                    }));
                    options.jvmArgs(getGraalVMBuilderExports());
                    Provider<String> richOutput = project.getProviders().systemProperty(RICH_OUTPUT_PROPERTY);
                    if (richOutput.isPresent()) {
                        options.getRichOutput().convention(richOutput.map(Boolean::parseBoolean));
                    }
                }
        );
        project.getPluginManager().withPlugin("application", plugin -> {
            TaskContainer tasks = project.getTasks();
            tasks.withType(BuildNativeImageTask.class).named("nativeCompile", nativeImageTask -> {
                MicronautRuntime mr = PluginsHelper.resolveRuntime(project);
                if (mr.isLambdaProvided()) {
                    DependencySet implementation = project.getConfigurations().getByName("implementation").getDependencies();
                    boolean isAwsApp = implementation.stream()
                            .noneMatch(dependency -> Objects.equals(dependency.getGroup(), "io.micronaut.aws") && dependency.getName().equals("micronaut-function-aws"));

                    if (isAwsApp) {
                        nativeImageTask.getOptions().get().getMainClass().set("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
                    }
                }
            });
        });
    }

    private void workaroundForResourcesDirectoryMissing(Project project) {
        project.getPluginManager().withPlugin("java", plugin ->
                project.afterEvaluate(unused -> {
                    // Workaround for https://github.com/graalvm/native-build-tools/issues/175
                    // and https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/306
                    project.getTasks().withType(ProcessResources.class).configureEach(task -> {
                        // yes we do this at config time, because otherwise the workaround
                        // simply doesn't work because there would be no inputs so that
                        // task would never be executed. So yes, this is incorrect because
                        // the destination dir _could_ be changed after this is executed
                        File destinationDir = task.getDestinationDir();
                        if (destinationDir != null) {
                            destinationDir.mkdirs();
                        }
                    });
                })
        );
    }

    private static void configureAnnotationProcessing(Project project, MicronautExtension extension) {
        SourceSetContainer sourceSets = project
                .getConvention()
                .getPlugin(JavaPluginConvention.class)
                .getSourceSets();
        project.afterEvaluate(unused -> {
            ListProperty<SourceSet> sets = extension.getProcessing().getAdditionalSourceSets();
            if (sets.isPresent()) {
                addGraalVMAnnotationProcessorDependency(project, sets.get());
            }
        });

        addGraalVMAnnotationProcessorDependency(project,
                sourceSets.stream()
                        .filter(sourceSet -> SOURCE_SETS.contains(sourceSet.getName()))
                        .toList()
        );
    }

    private static void addGraalVMAnnotationProcessorDependency(Project project, Iterable<SourceSet> sourceSets) {
        for (SourceSet sourceSet : sourceSets) {
            project.getDependencies().add(
                    sourceSet.getAnnotationProcessorConfigurationName(),
                    "io.micronaut:micronaut-graal"
            );
        }
    }

    public static List<String> getGraalVMBuilderExports() {
        return GRAALVM_MODULE_EXPORTS.stream()
                .map(module -> "--add-exports=" + module + "=ALL-UNNAMED")
                .toList();
    }
}
