/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.dev;

import io.micronaut.gradle.MicronautKotlinSupport;
import io.micronaut.gradle.PluginsHelper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import com.google.devtools.ksp.gradle.KspExtension;
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension;
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension;
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapperKt;
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

/**
 * Development mode for a Kotlin project: the Kotlin Build Tools implementation at the project's
 * Kotlin version, and KSP's implementation at the project's KSP version, on the
 * {@code mnDevCompiler} configuration; the Kotlin roots, outputs and options in the manifest; and
 * build-tool compilation for a KAPT project, which cannot be compiled embedded.
 *
 */
final class MicronautDevKotlinSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(MicronautDevKotlinSupport.class);
    private static final String KSP_GROUP = "com.google.devtools.ksp";
    private static final String KSP_PROCESSORS_CONFIGURATION = "mnDevKspProcessors";

    private MicronautDevKotlinSupport() {
    }

    /**
     * The Kotlin version the embedded compilation needs: the one where the Build Tools API the launcher
     * uses, {@code KotlinToolchains}, appeared.
     */
    static final String MINIMUM_EMBEDDED_KOTLIN = "2.3.0";

    static void configure(Project project, MicronautDevExtension dev, TaskProvider<MicronautDevManifest> manifest, Configuration compilers) {
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", unused -> {
            if (!isKotlinPluginPresent()) {
                return;
            }
            configureKotlin(project, dev, manifest, compilers);
        });
    }

    private static boolean isKotlinPluginPresent() {
        try {
            //noinspection ConstantConditions
            return KotlinJvmCompile.class != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void configureKotlin(Project project, MicronautDevExtension dev, TaskProvider<MicronautDevManifest> manifest, Configuration compilers) {
        String kotlinVersion = KotlinPluginWrapperKt.getKotlinPluginVersion(project);
        boolean embedded = isAtLeast(kotlinVersion, MINIMUM_EMBEDDED_KOTLIN);
        if (embedded) {
            compilers.getDependencies().add(project.getDependencies().create("org.jetbrains.kotlin:kotlin-build-tools-impl:" + kotlinVersion));
        } else {
            LOGGER.info("Kotlin {} is older than {}, which the embedded compilation needs: Kotlin sources are compiled by Gradle in development mode", kotlinVersion, MINIMUM_EMBEDDED_KOTLIN);
            manifest.configure(task -> task.getKotlinCompileMode().set("build-tool"));
        }
        project.getPluginManager().withPlugin("com.google.devtools.ksp", ksp -> {
            // ksp itself cannot be resolved: a resolvable view of it holds the processors and tells the KSP version
            Configuration kspProcessors = project.getConfigurations().create(KSP_PROCESSORS_CONFIGURATION, conf -> {
                conf.setCanBeConsumed(false);
                conf.setCanBeResolved(true);
                conf.setDescription("The symbol processors of the ksp configuration, for development mode");
                conf.extendsFrom(project.getConfigurations().getByName("ksp"));
            });
            if (embedded) {
                // the embedded KSP exists for the KSP of Kotlin 2 only: an older one compiles through Gradle anyway
                Provider<String> kspVersion = project.provider(() -> kspPluginVersion().orElse(null))
                    .orElse(kspProcessors.getIncoming().getResolutionResult().getRootComponent().map(MicronautDevKotlinSupport::kspVersionOf));
                compilers.getDependencies().addLater(kspVersion.map(version -> project.getDependencies().create(KSP_GROUP + ":symbol-processing-aa-embeddable:" + version)));
            }
            manifest.configure(task -> task.getProcessorPath().from(kspProcessors));
        });
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.kapt", kapt -> {
            LOGGER.info("KAPT cannot run inside the development JVM: Kotlin sources are compiled by Gradle in development mode");
            manifest.configure(task -> task.getKotlinCompileMode().set("build-tool"));
        });
        SourceSet main = PluginsHelper.findSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        KotlinJvmProjectExtension kotlin = project.getExtensions().getByType(KotlinJvmProjectExtension.class);
        // captured while the project is still being configured: a task's configuration runs too late for afterEvaluate
        Provider<List<String>> allOpen = MicronautKotlinSupport.isKotlinAllOpenSupportPresent() ? AllOpenOptions.captured(project) : null;
        manifest.configure(task -> {
            task.getKotlinSources().from(kotlin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getKotlin().getSrcDirs());
            task.getKotlinSources().from(MicronautDevSupport.dependencySourceDirectories(project, project.getConfigurations().getByName("developmentRuntimeClasspath"), "kotlin"));
            TaskProvider<KotlinJvmCompile> compileKotlin = project.getTasks().named(main.getCompileTaskName("kotlin"), KotlinJvmCompile.class);
            task.getKotlinOutput().set(project.provider(() -> compileKotlin.get().getDestinationDirectory().get().getAsFile().getAbsolutePath()));
            task.getKotlinOptions().set(project.provider(() -> kotlincOptions(compileKotlin.get())));
            if (allOpen != null) {
                task.getKotlinOptions().addAll(allOpen);
            }
            task.getKotlinOptions().addAll(dev.getKotlinCompilerArgs());
        });
    }

    /**
     * Whether a version is at least another, by its numeric segments.
     */
    static boolean isAtLeast(String version, String minimum) {
        String[] actual = version.split("[.-]");
        String[] wanted = minimum.split("[.-]");
        for (int i = 0; i < wanted.length; i++) {
            int a = i < actual.length && actual[i].matches("\\d+") ? Integer.parseInt(actual[i]) : 0;
            int w = wanted[i].matches("\\d+") ? Integer.parseInt(wanted[i]) : 0;
            if (a != w) {
                return a > w;
            }
        }
        return true;
    }

    /**
     * The KSP version, from the name of the KSP plugin's own jar on the build classpath:
     * {@code symbol-processing-gradle-plugin-<version>.jar}. Null when the jar is not named so.
     */
    private static Optional<String> kspPluginVersion() {
        try {
            java.security.CodeSource source = KspExtension.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                String name = new File(source.getLocation().toURI()).getName();
                String prefix = "symbol-processing-gradle-plugin-";
                if (name.startsWith(prefix) && name.endsWith(".jar")) {
                    return Optional.of(name.substring(prefix.length(), name.length() - ".jar".length()));
                }
            }
        } catch (java.net.URISyntaxException | SecurityException e) {
            LOGGER.debug("Cannot locate the KSP plugin jar: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * The KSP version from what the {@code ksp} configuration resolved, any KSP module of it, when the
     * plugin jar did not say.
     */
    private static String kspVersionOf(ResolvedComponentResult root) {
        java.util.ArrayDeque<ResolvedComponentResult> queue = new java.util.ArrayDeque<>(List.of(root));
        java.util.Set<ResolvedComponentResult> seen = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            ResolvedComponentResult component = queue.poll();
            if (!seen.add(component)) {
                continue;
            }
            ModuleVersionIdentifier id = component.getModuleVersion();
            if (id != null && KSP_GROUP.equals(id.getGroup())) {
                return id.getVersion();
            }
            for (var dependency : component.getDependencies()) {
                if (dependency instanceof ResolvedDependencyResult resolved) {
                    queue.add(resolved.getSelected());
                }
            }
        }
        throw new IllegalStateException("The ksp configuration resolves no " + KSP_GROUP + " module: the KSP version cannot be determined for development mode");
    }

    /**
     * The kotlinc options as the build passes them: the JVM target, Java parameter names, the free
     * arguments, the compiler plugins of the compilation, and the all-open annotations.
     */
    static List<String> kotlincOptions(KotlinJvmCompile compile) {
        List<String> options = new ArrayList<>();
        if (compile.getCompilerOptions().getJvmTarget().isPresent()) {
            options.add("-jvm-target");
            options.add(compile.getCompilerOptions().getJvmTarget().get().getTarget());
        }
        if (compile.getCompilerOptions().getJavaParameters().getOrElse(false)) {
            options.add("-java-parameters");
        }
        options.addAll(compile.getCompilerOptions().getFreeCompilerArgs().getOrElse(List.of()));
        for (File plugin : compile.getPluginClasspath().getFiles()) {
            options.add("-Xplugin=" + plugin.getAbsolutePath());
        }
        return options;
    }

    /**
     * The all-open options of the compilation when the all-open plugin is applied: the annotation the
     * Micronaut plugin configures, {@code io.micronaut.aop.Around}. The plugin keeps what a build adds to
     * the extension internal, so any other annotation or preset goes through {@code kotlinCompilerArgs}.
     * The all-open classes are touched by this class alone, so the outer one loads without the plugin present.
     */
    private static final class AllOpenOptions {

        private static final String AROUND = "io.micronaut.aop.Around";

        private AllOpenOptions() {
        }

        /**
         * The options as a provider that reads the extension captured once the build script ran: fit for
         * the configuration cache, since it holds no project.
         */
        static Provider<List<String>> captured(Project project) {
            java.util.concurrent.atomic.AtomicReference<AllOpenExtension> extension = new java.util.concurrent.atomic.AtomicReference<>();
            project.afterEvaluate(evaluated -> extension.set(evaluated.getExtensions().findByType(AllOpenExtension.class)));
            return project.getProviders().provider(() -> extension.get() == null
                ? List.of()
                : List.of("-P", "plugin:org.jetbrains.kotlin.allopen:annotation=" + AROUND));
        }
    }
}
