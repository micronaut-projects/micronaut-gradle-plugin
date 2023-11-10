package io.micronaut.gradle;

import com.google.devtools.ksp.gradle.KspExtension;
import io.micronaut.gradle.internal.AutomaticDependency;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension;
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile;
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions;
import org.jetbrains.kotlin.gradle.plugin.KaptExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static io.micronaut.gradle.PluginsHelper.CORE_VERSION_PROPERTY;
import static io.micronaut.gradle.PluginsHelper.configureAnnotationProcessors;
import static io.micronaut.gradle.PluginsHelper.resolveMicronautPlatform;

/**
 * Extension to integration support for Kotlin.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautKotlinSupport {
    private static final String[] KAPT_CONFIGURATIONS = new String[]{
        "kapt",
        "kaptTest"
    };
    private static final String[] KSP_CONFIGURATIONS = new String[]{
        "ksp",
        "kspTest"
    };
    public static final String KOTLIN_PROCESSORS = "kotlinProcessors";

    private static final List<String> KSP_ANNOTATION_PROCESSOR_MODULES = Arrays.asList("inject-kotlin");

    public static void whenKotlinSupportPresent(Project p, Consumer<? super Project> action) {
        p.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", unused -> action.accept(p));
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
     * Configure Kotlin.
     *
     * @param project The project
     */
    public static void configureKotlin(Project project) {
        PluginManager pluginManager = project.getPluginManager();
        final TaskContainer tasks = project.getTasks();
        project.getConfigurations().create(KOTLIN_PROCESSORS, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeConsumed(false);
        });
        tasks.withType(KotlinCompile.class).configureEach(kotlinCompile -> {
            final KotlinJvmOptions kotlinOptions = (KotlinJvmOptions) kotlinCompile.getKotlinOptions();
            kotlinOptions.setJavaParameters(true);
        });
        pluginManager.withPlugin("org.jetbrains.kotlin.plugin.allopen", unused -> configureAllOpen(project));
        pluginManager.withPlugin("org.jetbrains.kotlin.kapt", unused -> configureKapt(project));
        pluginManager.withPlugin("com.google.devtools.ksp", unused -> configureKsp(project));
    }

    private static void configureKsp(Project project) {
        configureKotlinCompilerPlugin(project, KSP_CONFIGURATIONS, "ksp", KSP_ANNOTATION_PROCESSOR_MODULES);

        final ExtensionContainer extensions = project.getExtensions();
        extensions.configure(KspExtension.class, kspExtension -> {
            final MicronautExtension micronautExtension = extensions.getByType(MicronautExtension.class);
            AnnotationProcessing processingConfig = micronautExtension.getProcessing();
            final boolean isIncremental = processingConfig.getIncremental().getOrElse(true);
            final String group = processingConfig.getGroup().getOrElse(project.getGroup().toString());
            final String module = processingConfig.getModule().getOrElse(project.getName());
            if (isIncremental) {
                kspExtension.arg("micronaut.processing.incremental", "true");
                if (group.length() > 0) {
                    kspExtension.arg("micronaut.processing.group", group);
                }
                kspExtension.arg("micronaut.processing.module", module);
            }
        });
    }

    private static void configureKapt(Project project) {
        configureKotlinCompilerPlugin(project, KAPT_CONFIGURATIONS, "kapt", PluginsHelper.ANNOTATION_PROCESSOR_MODULES);

        final ExtensionContainer extensions = project.getExtensions();
        extensions.configure(KaptExtension.class, kaptExtension -> {
            kaptExtension.setGenerateStubs(false);
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
                        options.arg("micronaut.processing.group", group);
                    }
                    options.arg("micronaut.processing.module", module);

                    return null;
                });
            }
        });
    }

    private static void configureKotlinCompilerPlugin(Project project, String[] compilerConfigurations, String compilerType, List<String> annotationProcessorModules) {
        // add inject-java to kapt scopes
        PluginsHelper.registerAnnotationProcessors(project, annotationProcessorModules, compilerConfigurations);
        addGraalVmDependencies(compilerConfigurations, project);

        Configuration kotlinProcessors = project.getConfigurations().getByName(KOTLIN_PROCESSORS);
        for (String compilerConfiguration : compilerConfigurations) {
            project.getConfigurations().getByName(compilerConfiguration).extendsFrom(kotlinProcessors);
        }
        PluginsHelper.applyAdditionalProcessors(
            project,
            compilerConfigurations
        );
        var registry = project.
            getExtensions()
            .getByType(SourceSetConfigurerRegistry.class);
        var dependencyHandler = project.getDependencies();
        var platform = PluginsHelper.findMicronautVersion(project).map(micronautVersion -> resolveMicronautPlatform(dependencyHandler, micronautVersion));
        var knownSourceSets = new HashSet<SourceSet>();
        registry.register(sourceSet -> {
            configureAdditionalSourceSet(compilerType, project, dependencyHandler, platform, sourceSet);
            knownSourceSets.add(sourceSet);
        });
        for (String compileConfiguration : compilerConfigurations) {
            dependencyHandler.addProvider(
                compileConfiguration,
                platform
            );
        }
        project.afterEvaluate(p -> {
            PluginsHelper.applyAdditionalProcessors(
                p,
                compilerConfigurations
            );
            configureExtraSourceSetsUsingDeprecatedBehavior(compilerType, p, knownSourceSets, dependencyHandler, platform);
        });

    }

    private static void configureExtraSourceSetsUsingDeprecatedBehavior(String compilerType, Project p, HashSet<SourceSet> knownSourceSets, DependencyHandler dependencyHandler, Provider<Dependency> platform) {
        var micronautExtension = p
            .getExtensions()
            .getByType(MicronautExtension.class);
        @SuppressWarnings("deprecation")
        var additionalSourceSets =
            micronautExtension.getProcessing()
                .getAdditionalSourceSets();
        if (additionalSourceSets.isPresent()) {
            List<SourceSet> configurations = additionalSourceSets.get();
            if (!configurations.isEmpty()) {
                for (SourceSet sourceSet : configurations) {
                    if (!knownSourceSets.contains(sourceSet)) {
                        AnnotationProcessing.showAdditionalSourceSetDeprecationWarning(sourceSet);
                        configureAdditionalSourceSet(compilerType, p, dependencyHandler, platform, sourceSet);
                    }
                }
            }
        }
    }

    private static void configureAdditionalSourceSet(String compilerType,
                                                     Project p,
                                                     DependencyHandler dependencyHandler,
                                                     Provider<Dependency> platform,
                                                     SourceSet sourceSet) {
        String annotationProcessorConfigurationName = compilerType + Strings.capitalize(sourceSet.getName());
        String implementationConfigurationName = sourceSet
            .getImplementationConfigurationName();
        List<String> both = Arrays.asList(
            implementationConfigurationName,
            annotationProcessorConfigurationName
        );
        for (String configuration : both) {
            dependencyHandler.addProvider(
                configuration,
                platform
            );
        }
        configureAnnotationProcessors(p,
            implementationConfigurationName,
            annotationProcessorConfigurationName);
        p.getPluginManager().withPlugin("io.micronaut.graalvm", unused ->
            new AutomaticDependency(annotationProcessorConfigurationName,
                "io.micronaut:micronaut-graal",
                Optional.of(CORE_VERSION_PROPERTY)).applyTo(p)
        );
    }

    private static void addGraalVmDependencies(String[] compilerConfigurations, Project project) {
        project.getPluginManager().withPlugin("io.micronaut.graalvm", unused -> {
            for (String configuration : compilerConfigurations) {
                new AutomaticDependency(configuration,
                    "io.micronaut:micronaut-graal",
                    Optional.of(CORE_VERSION_PROPERTY)).applyTo(project);
            }
        });
    }

    private static void configureAllOpen(Project project) {
        AllOpenExtension allOpen = project.getExtensions().getByType(AllOpenExtension.class);
        allOpen.annotation("io.micronaut.aop.Around");
    }

}
