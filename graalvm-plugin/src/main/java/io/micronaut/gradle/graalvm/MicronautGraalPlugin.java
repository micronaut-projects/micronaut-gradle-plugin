package io.micronaut.gradle.graalvm;

import io.micronaut.gradle.AnnotationProcessing;
import io.micronaut.gradle.MicronautComponentPlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.MicronautRuntime;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.SourceSetConfigurerRegistry;
import io.micronaut.gradle.internal.AutomaticDependency;
import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.gradle.PluginsHelper.CORE_VERSION_PROPERTY;
import static io.micronaut.gradle.PluginsHelper.findMicronautExtension;

/**
 * Support for building GraalVM native images.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.0.0
 */
public class MicronautGraalPlugin implements Plugin<Project> {

    public static final String RICH_OUTPUT_PROPERTY = "io.micronaut.graalvm.rich.output";

    private static final Set<String> SOURCE_SETS = Set.of("main", "test");
    private static final List<String> GRAALVM_MODULE_EXPORTS = List.of(
            "org.graalvm.nativeimage.builder/com.oracle.svm.core.configure",
            "org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk",
            "org.graalvm.nativeimage.builder/com.oracle.svm.core.jni",
            "org.graalvm.sdk/org.graalvm.nativeimage.impl"
    );

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeImagePlugin.class);
        workaroundForResourcesDirectoryMissing(project);
        project.getPluginManager().withPlugin("io.micronaut.minimal.library", plugin -> {
            MicronautExtension extension = PluginsHelper.findMicronautExtension(project);
            configureAnnotationProcessing(project, extension);
        });
        project.getPluginManager().withPlugin("io.micronaut.minimal.application", plugin -> {
            MicronautExtension extension = PluginsHelper.findMicronautExtension(project);
            configureAnnotationProcessing(project, extension);
        });
        project.getPlugins().withType(MicronautComponentPlugin.class, unused -> {
            var extension = PluginsHelper.findMicronautExtension(project);
            var nativeLambdaExtension = extension.getExtensions().create("nativeLambda", NativeLambdaExtension.class);
            nativeLambdaExtension.getLambdaRuntime().convention(NativeLambdaRuntime.API_GATEWAY_V1);
            nativeLambdaExtension.getLambdaRuntimeClassName().convention(nativeLambdaExtension.getLambdaRuntime().map(NativeLambdaRuntime::getMainClassName));
        });
        GraalVMExtension graal = project.getExtensions().findByType(GraalVMExtension.class);
        GraalVMReachabilityMetadataRepositoryExtension reachability = ((ExtensionAware) graal).getExtensions().getByType(GraalVMReachabilityMetadataRepositoryExtension.class);
        reachability.getEnabled().convention(true);
        graal.getBinaries().configureEach(options ->
                {
                    options.resources(rsrc -> rsrc.autodetection(inf -> {
                        inf.getEnabled().convention(true);
                        inf.getIgnoreExistingResourcesConfigFile().convention(true);
                        inf.getRestrictToProjectDependencies().convention(true);
                    }));
                    Provider<String> richOutput = project.getProviders().systemProperty(RICH_OUTPUT_PROPERTY);
                    if (richOutput.isPresent()) {
                        options.getRichOutput().convention(richOutput.map(Boolean::parseBoolean));
                    }
                }
        );
        TaskContainer tasks = project.getTasks();
        project.getPluginManager().withPlugin("application", plugin ->
            tasks.withType(BuildNativeImageTask.class).named("nativeCompile", nativeImageTask -> {
                MicronautRuntime mr = PluginsHelper.resolveRuntime(project);
                if (mr.isLambdaProvided()) {
                    DependencySet implementation = project.getConfigurations().getByName("implementation").getDependencies();
                    boolean isAwsApp = implementation.stream()
                            .noneMatch(dependency -> Objects.equals(dependency.getGroup(), "io.micronaut.aws") && dependency.getName().equals("micronaut-function-aws"));

                    if (isAwsApp) {
                        var nativeLambdaExtension = findMicronautExtension(project).getExtensions().getByType(NativeLambdaExtension.class);
                        nativeImageTask.getOptions().get().getMainClass().set(nativeLambdaExtension.getLambdaRuntimeClassName());
                    }
                }
        }));
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
        var registry = project.getExtensions().getByType(SourceSetConfigurerRegistry.class);
        var knownSourceSets = new HashSet<SourceSet>();
        registry.register(sourceSet -> {
            addGraalVMAnnotationProcessorDependency(project, Set.of(sourceSet));
            knownSourceSets.add(sourceSet);
        });
        var sourceSets = PluginsHelper.findSourceSets(project);
        project.afterEvaluate(unused -> {
            @SuppressWarnings("deprecation")
            var sets = extension.getProcessing().getAdditionalSourceSets();
            var additionalSourceSets = sets.get();
            for (SourceSet sourceSet : additionalSourceSets) {
                if (!knownSourceSets.contains(sourceSet)) {
                    AnnotationProcessing.showAdditionalSourceSetDeprecationWarning(sourceSet);
                    registry.register(sourceSet1 -> addGraalVMAnnotationProcessorDependency(project, Set.of(sourceSet)));
                }
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
            new AutomaticDependency(sourceSet.getAnnotationProcessorConfigurationName(),
                    "io.micronaut:micronaut-graal",
                    Optional.of(CORE_VERSION_PROPERTY)).applyTo(project);
        }
    }

    public static List<String> getGraalVMBuilderExports() {
        return GRAALVM_MODULE_EXPORTS.stream()
                .map(module -> "--add-exports=" + module + "=ALL-UNNAMED")
                .toList();
    }

    /**
     * This method isn't used directly in the plugin, but provided as a convenience
     * for users in case a Micronaut module they are using is still, for whatever
     * reason, using internal GraalVM APIs. This shouldn't be the case since Micronaut 4
     * but there may be community modules which are still doing this.
     * In this case the user can directly call this method to add the required exports.
     *
     * @param options the native binary on which to add options
     */
    @SuppressWarnings("unused")
    public static void applyGraalVMBuilderExportsOn(NativeImageOptions options) {
        options.jvmArgs(getGraalVMBuilderExports());
    }
}
