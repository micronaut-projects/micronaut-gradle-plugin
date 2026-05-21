/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle;

import org.cyclonedx.gradle.CyclonedxDirectTask;
import org.cyclonedx.model.Component;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import java.util.Map;

final class CycloneDxSbomSupport {
    private static final String CYCLONEDX_PLUGIN_ID = "org.cyclonedx.bom";
    private static final String CYCLONEDX_DIRECT_BOM_TASK = "cyclonedxDirectBom";
    private static final String SBOM_FORMAT = "CycloneDX JSON";
    private static final String SBOM_FORMAT_ATTRIBUTE = "Micronaut-SBOM-Format";
    private static final String SBOM_LOCATION_ATTRIBUTE = "Micronaut-SBOM-Location";

    private CycloneDxSbomSupport() {
    }

    static void configure(Project project, String projectType) {
        MicronautExtension micronaut = project.getExtensions().getByType(MicronautExtension.class);
        MicronautSbomExtension sbom = micronaut.getSbom();
        sbom.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("reports/micronaut/sbom"));

        project.getPluginManager().withPlugin(CYCLONEDX_PLUGIN_ID, unused -> {
            project.afterEvaluate(evaluatedProject -> {
                if (sbom.getEnabled().get()) {
                    Provider<RegularFile> sbomFile = sbom.getOutputDirectory().file(sbom.getOutputFileName());
                    TaskProvider<CyclonedxDirectTask> cyclonedxDirectBom = project.getTasks()
                        .named(CYCLONEDX_DIRECT_BOM_TASK, CyclonedxDirectTask.class, task -> {
                            task.getJsonOutput().convention(sbomFile);
                            task.getXmlOutput().unsetConvention();
                            task.getIncludeConfigs().convention(sbom.getIncludeConfigurations());
                            task.getSkipConfigs().convention(sbom.getSkipConfigurations());
                            task.getProjectType().convention(Component.Type.valueOf(projectType));
                            task.getIncludeBuildEnvironment().convention(false);
                            task.getIncludeBuildSystem().convention(false);
                            task.getIncludeBomSerialNumber().convention(false);
                            task.getIncludeLicenseText().convention(false);
                        });
                    configureJarPackaging(project, sbom, sbomFile, cyclonedxDirectBom);
                }
            });
        });
    }

    private static void configureJarPackaging(Project project,
                                              MicronautSbomExtension sbom,
                                              Provider<RegularFile> sbomFile,
                                              TaskProvider<CyclonedxDirectTask> cyclonedxDirectBom) {
        if (!sbom.getIncludeInJar().get()) {
            return;
        }
        String sbomPathInJar = sbom.getSbomPathInJar().get();
        project.getTasks().named("jar", Jar.class, jar -> configureJar(jar, sbomFile, cyclonedxDirectBom, sbomPathInJar));
        project.getTasks().withType(Jar.class).configureEach(jar -> {
            if ("runnerJar".equals(jar.getName())) {
                configureJar(jar, sbomFile, cyclonedxDirectBom, sbomPathInJar);
            }
        });
    }

    private static void configureJar(Jar jar,
                                     Provider<RegularFile> sbomFile,
                                     TaskProvider<CyclonedxDirectTask> cyclonedxDirectBom,
                                     String sbomPathInJar) {
        jar.dependsOn(cyclonedxDirectBom);
        jar.from(sbomFile, copySpec -> {
            copySpec.into(directoryOf(sbomPathInJar));
            copySpec.rename(name -> fileNameOf(sbomPathInJar));
        });
        jar.getManifest().attributes(Map.of(
            SBOM_FORMAT_ATTRIBUTE, SBOM_FORMAT,
            SBOM_LOCATION_ATTRIBUTE, sbomPathInJar
        ));
    }

    private static String directoryOf(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }

    private static String fileNameOf(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }
}
