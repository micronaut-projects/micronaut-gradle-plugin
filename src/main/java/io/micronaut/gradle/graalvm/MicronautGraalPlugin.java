package io.micronaut.gradle.graalvm;

import com.bmuschko.gradle.docker.shaded.com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.bmuschko.gradle.docker.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import com.bmuschko.gradle.docker.shaded.com.fasterxml.jackson.databind.ObjectWriter;
import io.micronaut.gradle.AnnotationProcessing;
import io.micronaut.gradle.MicronautApplicationPlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.MicronautRuntime;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Support for building GraalVM native images.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.0.0
 */
public class MicronautGraalPlugin implements Plugin<Project> {

    private static final List<String> DEPENDENT_CONFIGURATIONS = Arrays.asList(JavaPlugin.API_CONFIGURATION_NAME, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);

    private static final String META_INF = "META-INF";
    private static final String RESOURCES = "resources";
    private static final String PATTERN = "pattern";
    private static final String RESOURCE_CONFIG_JSON = "resource-config.json";
    private static final List<String> EXCLUDED_META_INF_DIRECTORIES = Arrays.asList("native-image", "services");

    private final ObjectWriter writer = new ObjectMapper().writer(new DefaultPrettyPrinter());

    @Override
    public void apply(Project project) {
        project.afterEvaluate(p -> {
            MicronautExtension extension = p.getExtensions().getByType(MicronautExtension.class);
            if (extension.getEnableNativeImage().getOrElse(true)) {
                p.getGradle().getTaskGraph().whenReady(taskGraph -> {
                    TaskContainer tasks = p.getTasks();
                    final Task nativeImage = tasks.findByName("nativeImage");
                    final Task dockerfileNative = tasks.findByName("dockerfileNative");
                    boolean addGraalProcessor = nativeImage != null && taskGraph.hasTask(nativeImage) || (dockerfileNative != null && taskGraph.hasTask(dockerfileNative));
                    if (addGraalProcessor) {
                        SourceSetContainer sourceSets = p.getConvention().getPlugin(JavaPluginConvention.class)
                                .getSourceSets();
                        for (String sourceSetName : Arrays.asList("main", "test")) {
                            SourceSet sourceSet = sourceSets.findByName(sourceSetName);
                            if (sourceSet != null) {
                                p.getDependencies().add(
                                        sourceSet.getAnnotationProcessorConfigurationName(),
                                        "io.micronaut:micronaut-graal"
                                );
                            }
                        }
                        ListProperty<SourceSet> additionalSourceSets = extension.getProcessing().getAdditionalSourceSets();
                        if (additionalSourceSets.isPresent()) {
                            List<SourceSet> sourceSetList = additionalSourceSets.get();
                            for (SourceSet sourceSet : sourceSetList) {
                                p.getDependencies().add(
                                        sourceSet.getAnnotationProcessorConfigurationName(),
                                        "io.micronaut:micronaut-graal"
                                );
                            }
                        }
                    }
                });


            }
        });

        if (project.getPlugins().hasPlugin("application")) {
            TaskContainer tasks = project.getTasks();
            TaskProvider<NativeImageTask> nit = tasks.register("nativeImage", NativeImageTask.class, nativeImageTask -> {
                MicronautRuntime mr = MicronautApplicationPlugin.resolveRuntime(project);
                if (mr == MicronautRuntime.LAMBDA) {
                    DependencySet implementation = project.getConfigurations().getByName("implementation").getDependencies();
                    boolean isAwsApp = implementation.stream()
                            .noneMatch(dependency -> Objects.equals(dependency.getGroup(), "io.micronaut.aws") && dependency.getName().equals("micronaut-function-aws"));

                    if (isAwsApp) {
                        nativeImageTask.setMain("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
                    }
                }
                nativeImageTask.dependsOn(tasks.findByName("classes"));
                nativeImageTask.setGroup(BasePlugin.BUILD_GROUP);
                nativeImageTask.setDescription("Builds a GraalVM Native Image");
            });

            project.afterEvaluate(p -> p
                    .getConfigurations()
                    .configureEach(configuration -> {
                        if (DEPENDENT_CONFIGURATIONS.contains(configuration.getName())) {
                            final DependencySet dependencies = configuration.getDependencies();
                            for (Dependency dependency : dependencies) {
                                if (dependency instanceof ProjectDependency) {
                                    final Project otherProject = ((ProjectDependency) dependency).getDependencyProject();
                                    otherProject.getTasks().withType(Jar.class, jar -> {
                                        if (jar.getName().equals("jar")) {
                                            nit.configure(nativeImageTask -> nativeImageTask.dependsOn(jar));
                                        }
                                    });
                                }
                            }
                        }
                    }));

            tasks.withType(Test.class, (test ->
                tasks.register(test.getName() + "NativeImage", nativeImageTestTask -> {
                    nativeImageTestTask.doLast((t) -> {
                        NativeImageTask nativeImage = nit.get();
                        File file = nativeImage.getNativeImageOutput();
                        test.systemProperty("micronaut.test.server.executable", file.getAbsolutePath());
                    });
                    boolean enabled = test.isEnabled() && GraalUtil.isGraalJVM();
                    nativeImageTestTask.onlyIf(task -> {
                        boolean isGraal = GraalUtil.isGraalJVM();
                        if (!isGraal) {
                            project.getLogger().log(LogLevel.INFO, "Skipping testNativeImage because the configured JDK is not a GraalVM JDK");
                        }
                        return isGraal;
                    });
                    if (enabled) {
                        nativeImageTestTask.dependsOn(nit);
                        test.mustRunAfter(nativeImageTestTask);
                        nativeImageTestTask.finalizedBy(test);
                    }
                    nativeImageTestTask.setDescription("Runs tests against a native image build of the server. Requires the server to allow the port to configurable with 'micronaut.server.port'.");
            })));

            tasks.withType(AbstractCompile.class, compileTask -> {
                compileTask.doLast(task -> {
                    Map<String, Object> json = new HashMap<>();

                    SourceSetContainer sourceSets = project.getConvention()
                            .getPlugin(JavaPluginConvention.class)
                            .getSourceSets();

                    SourceSet sourceSet = sourceSets.findByName("main");

                    Set<String> resourcesToAdd = new HashSet<>();
                    if (sourceSet != null) {
                        List<Path> resourceDirectories = sourceSet
                                .getResources()
                                .getSrcDirs()
                                .stream()
                                .map(File::toPath)
                                .collect(Collectors.toList());

                        // Application resources (src/main/resources)
                        for (Path resourceDirectory : resourceDirectories) {
                            resourcesToAdd.addAll(findResourceFiles(resourceDirectory.toFile()));
                        }

                        for (File classesDir : sourceSet.getOutput().getClassesDirs()) {
                            Path metaInfPath = Paths.get(classesDir.getAbsolutePath(), META_INF);

                            // Generated resources (like openapi)
                            resourcesToAdd.addAll(findResourceFiles(metaInfPath.toFile(), Collections.singletonList(META_INF)));

                            Path nativeImagePath = buildNativeImagePath(project);
                            Path graalVMResourcesPath = metaInfPath.resolve(nativeImagePath).toAbsolutePath();

                            List<Map> resourceList = resourcesToAdd.stream()
                                    .map(this::mapToGraalResource)
                                    .collect(Collectors.toList());

                            json.put(RESOURCES, resourceList);

                            try {
                                Files.createDirectories(graalVMResourcesPath);
                                File resourceConfigFile = graalVMResourcesPath.resolve(RESOURCE_CONFIG_JSON).toFile();
                                System.out.println("Generating " + resourceConfigFile.getAbsolutePath());
                                writer.writeValue(resourceConfigFile, json);

                            } catch (IOException e) {
                                throw new GradleException("There was an error generating GraalVM resource-config.json file", e);
                            }
                        }
                    }
                });
            });

            project.afterEvaluate(p -> p.getTasks().withType(NativeImageTask.class, nativeImageTask -> {
                if (!nativeImageTask.getName().equals("internalDockerNativeImageTask")) {
                    MicronautExtension extension = project.getExtensions().getByType(MicronautExtension.class);
                    nativeImageTask.setEnabled(extension.getEnableNativeImage().getOrElse(false));
                    JavaApplication javaApplication = p.getExtensions().getByType(JavaApplication.class);
                    String mainClassName = javaApplication.getMainClass().getOrNull();
                    String imageName = p.getName();
                    if (mainClassName != null && !nativeImageTask.getMain().isPresent()) {
                        nativeImageTask.setMain(mainClassName);
                    }
                    if (!nativeImageTask.getImageName().isPresent()) {
                        nativeImageTask.setImageName(imageName);
                    }
                }
            }));
        }
    }

    private Set<String> findResourceFiles(File folder) {
        return this.findResourceFiles(folder, null);
    }

    private Set<String> findResourceFiles(File folder, List<String> filePath) {
        Set<String> resourceFiles = new HashSet<>();

        if (filePath == null) {
            filePath = new ArrayList<>();
        }

        if (folder.exists()) {
            File[] files = folder.listFiles();

            if (files != null) {
                boolean isMetaInfDirectory = folder.getName().equals(META_INF);

                for (File element : files) {
                    boolean isExcludedDirectory = EXCLUDED_META_INF_DIRECTORIES.contains(element.getName());
                    // Exclude some directories in 'META-INF' like 'native-image' and 'services' but process other
                    // 'META-INF' files and directories, for example, to include swagger-ui.
                    if (!isMetaInfDirectory || !isExcludedDirectory) {
                        if (element.isDirectory()) {
                            List<String> paths = new ArrayList<>(filePath);
                            paths.add(element.getName());

                            resourceFiles.addAll(findResourceFiles(element, paths));
                        } else {
                            String joinedDirectories = String.join("/", filePath);
                            String elementName = joinedDirectories.isEmpty() ? element.getName() : joinedDirectories + "/" + element.getName();

                            resourceFiles.add(elementName);
                        }
                    }
                }
            }
        }

        return resourceFiles;
    }

    private Path buildNativeImagePath(Project project) {
        MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
        AnnotationProcessing processing = micronautExtension.getProcessing();

        String group = processing.getGroup().getOrElse(project.getGroup().toString());
        String module = processing.getModule().getOrElse(project.getName());

        return Paths.get("native-image", group, module);
    }

    private Map mapToGraalResource(String resourceName) {
        Map<String, String> result = new HashMap<>();

        if (resourceName.contains("*")) {
            result.put(PATTERN, resourceName);
        } else {
            result.put(PATTERN, "\\Q" + resourceName + "\\E");
        }

        return result;
    }
}
