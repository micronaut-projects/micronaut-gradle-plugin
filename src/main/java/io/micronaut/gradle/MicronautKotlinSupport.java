package io.micronaut.gradle;

import io.micronaut.gradle.graalvm.GraalUtil;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension;
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile;
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions;
import org.jetbrains.kotlin.gradle.plugin.KaptExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Locale.ENGLISH;

/**
 * Extension to integration support for Kotlin.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautKotlinSupport {

    /**
     * Check whether Kotlin support is present.
     *
     * @return True if Kotlin support is present
     */
    public static boolean isKotlinSupportPresent() {
        try {
            //noinspection ConstantConditions
            return KotlinCompile.class != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Check whether Kotlin support is present.
     *
     * @return True if Kotlin support is present
     */
    public static boolean isKotlinAllOpenSupportPresent() {
        try {
            //noinspection ConstantConditions
            return AllOpenExtension.class != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Check whether Kotlin support is present.
     *
     * @return True if Kotlin support is present
     */
    public static boolean isKotlinJvmPresent() {
        try {
            //noinspection ConstantConditions
            return KotlinJvmOptions.class != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Configure Kotlin.
     *
     * @param project The project
     */
    public static void configureKotlin(Project project) {
        final PluginContainer plugins = project.getPlugins();
        boolean hasKotlin = plugins.findPlugin("org.jetbrains.kotlin.kapt") != null;
        if (!hasKotlin || !isKotlinSupportPresent()) {
            return;
        }
        plugins.apply("org.jetbrains.kotlin.plugin.allopen");
        plugins.apply("org.jetbrains.kotlin.kapt");

        // configure all open
        if (isKotlinAllOpenSupportPresent()) {
            final AllOpenExtension allOpen = project.getExtensions()
                    .getByType(AllOpenExtension.class);
            allOpen.annotation("io.micronaut.aop.Around");
        }

        // add inject-java to kapt scopes
        List<String> kaptConfigs = Arrays.asList("kapt", "kaptTest");
        final DependencyHandler dependencies = project.getDependencies();
        for (String kaptConfig : kaptConfigs) {
            List<String> modules = MicronautLibraryPlugin.getAnnotationProcessorModules();
            for (String module : modules) {
                dependencies.add(
                        kaptConfig,
                        "io.micronaut:micronaut-" + module
                );
            }
        }


        if (GraalUtil.isGraalJVM()) {
            for (String configuration : kaptConfigs) {
                dependencies.add(
                        configuration,
                        "io.micronaut:micronaut-graal"
                );
            }
        }

        project.afterEvaluate(p -> {
            MicronautLibraryPlugin.applyAdditionalProcessors(
                    p,
                    "kapt", "kaptTest"
            );
            final MicronautExtension micronautExtension = p
                    .getExtensions()
                    .getByType(MicronautExtension.class);
            ListProperty<SourceSet> additionalSourceSets =
                    micronautExtension.getProcessing().getAdditionalSourceSets();
            final DependencyHandler dependencyHandler = p.getDependencies();
            final String micronautVersion = MicronautLibraryPlugin.getMicronautVersion(
                    p,
                    micronautExtension
            );

            final Dependency platform = MicronautLibraryPlugin.resolveMicronautPlatform(dependencyHandler, micronautVersion);
            if (additionalSourceSets.isPresent()) {
                List<SourceSet> configurations = additionalSourceSets.get();
                if (!configurations.isEmpty()) {
                    for (SourceSet sourceSet : configurations) {
                        String annotationProcessorConfigurationName = "kapt" + capitalize(sourceSet.getName());
                        String implementationConfigurationName = sourceSet
                                .getImplementationConfigurationName();
                        List<String> both = Arrays.asList(
                                implementationConfigurationName,
                                annotationProcessorConfigurationName
                        );
                        for (String configuration : both) {
                            dependencyHandler.add(
                                    configuration,
                                    platform
                            );
                        }
                        MicronautLibraryPlugin.configureAnnotationProcessors(p,
                                implementationConfigurationName,
                                Collections.singletonList(
                                        annotationProcessorConfigurationName
                                ));
                    }
                }
            }


            for (String kaptConfig : kaptConfigs) {
                dependencyHandler.add(
                        kaptConfig,
                        platform
                );
            }
        });

        final TaskContainer tasks = project.getTasks();
        tasks.withType(KotlinCompile.class, kotlinCompile -> {
            final KotlinJvmOptions kotlinOptions = (KotlinJvmOptions) kotlinCompile.getKotlinOptions();
            kotlinOptions.setJavaParameters(true);
        });

        if (isKotlinJvmPresent()) {
            final ExtensionContainer extensions = project.getExtensions();
            extensions.configure(KaptExtension.class, kaptExtension -> {
                final MicronautExtension micronautExtension = extensions.getByType(MicronautExtension.class);
                AnnotationProcessing processingConfig = micronautExtension.getProcessing();
                final boolean isIncremental = processingConfig.getIncremental().getOrElse(true);
                final String group = processingConfig.getGroup().getOrElse(project.getGroup().toString());
                final String module = processingConfig.getModule().getOrElse(project.getName());
                if (isIncremental) {

                    kaptExtension.arguments(options -> {
                        options.arg("micronaut.processing.incremental", true);
                        final List<String> annotations = processingConfig.getAnnotations().getOrElse(Collections.emptyList());
                        if (!annotations.isEmpty()) {
                            options.arg("micronaut.processing.annotations", String.join(",", annotations));
                        } else {
                            if (group.length() > 0) {
                                options.arg("micronaut.processing.annotations", group + ".*");
                            }
                        }

                        if (group.length() > 0) {
                            options.arg("micronaut.processing.group,", group);
                        }
                        options.arg("micronaut.processing.module", module);

                        return null;
                    });
                }
            });

        }
    }

    private static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }
}
