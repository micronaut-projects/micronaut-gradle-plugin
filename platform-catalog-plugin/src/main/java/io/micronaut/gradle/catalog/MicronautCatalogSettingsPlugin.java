/*
 * Copyright 2003-2021 the original author or authors.
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
package io.micronaut.gradle.catalog;

import org.gradle.api.Plugin;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.jetbrains.annotations.NotNull;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public abstract class MicronautCatalogSettingsPlugin implements Plugin<Settings> {
    private static final Logger LOGGER = Logging.getLogger(MicronautCatalogSettingsPlugin.class);
    public static final String OVERRIDE_VERSIONS_TOML_FILE = "override.versions.toml";

    abstract RegularFileProperty getDefaultGradleVersionCatalogFile();

    @Override
    public void apply(Settings settings) {
        ProviderFactory providers = settings.getProviders();
        Provider<String> micronautVersion = createMicronautVersionProvider(settings, providers);
        registerMicronautVersionCatalog(settings, micronautVersion);
    }

    private static void registerMicronautVersionCatalog(Settings settings, Provider<String> micronautVersionProvider) {
        settings.getGradle().settingsEvaluated(unused ->
                settings.dependencyResolutionManagement(drm -> {
                            String micronautVersion = micronautVersionProvider.get();
                            if (drm.getRepositories().isEmpty()) {
                                drm.getRepositories().mavenCentral();
                                if (micronautVersion.endsWith("-SNAPSHOT")) {
                                    drm.getRepositories().maven(repo -> {
                                        repo.setName("Micronaut Snapshots");
                                        repo.setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/");
                                    });
                                }
                            }
                            drm.versionCatalogs(vcs -> {
                                vcs.create("mn", catalog -> catalog.from("io.micronaut.platform:micronaut-platform:" + micronautVersion));
                                vcs.configureEach(catalog -> {
                                    File catalogOverrideFile = new File(settings.getSettingsDir(), "gradle/" + catalog.getName() + "-" + OVERRIDE_VERSIONS_TOML_FILE);
                                    if (catalogOverrideFile.exists()) {
                                        LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
                                        try (InputStream in = new FileInputStream(catalogOverrideFile)) {
                                            parser.parse(in);
                                            VersionCatalogTomlModel model = parser.getModel();
                                            fixupMicronautCatalogWith(catalog, model);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                            });
                        }
                ));
    }

    private static void fixupMicronautCatalogWith(VersionCatalogBuilder catalog,
                                                  VersionCatalogTomlModel model) {
        model.getVersionsTable().forEach(versionModel -> {
            RichVersion version = versionModel.getVersion();
            String reference = versionModel.getReference();
            if (reference != null) {
                catalog.version(reference, vc -> {
                    String strictly = version.getStrictly();
                    if (strictly != null) {
                        vc.strictly(strictly);
                    } else {
                        String require = version.getRequire();
                        if (require != null) {
                            vc.require(require);
                        }
                    }
                    String prefer = version.getPrefer();
                    if (prefer != null) {
                        vc.prefer(prefer);
                    }
                    if (version.isRejectAll()) {
                        vc.rejectAll();
                    } else {
                        List<String> rejectedVersions = version.getRejectedVersions();
                        if (rejectedVersions != null) {
                            rejectedVersions.forEach(vc::reject);
                        }
                    }
                });
            }
        });
        if (!model.getLibrariesTable().isEmpty()) {
            LOGGER.warn("The " + OVERRIDE_VERSIONS_TOML_FILE + " file should only contain entries overriding the Micronaut Platform versions. Use your own version catalog to declare new libraries");
        }
    }

    @NotNull
    private Provider<String> createMicronautVersionProvider(Settings settings, ProviderFactory providers) {
        return providers.gradleProperty("micronautVersion")
                .orElse(readFromVersionCatalog(settings))
                .orElse(providers.provider(() -> {
                    throw new IllegalStateException("Micronaut version must either be declared in `gradle.properties`, in `gradle/libs.version.toml`");
                }));
    }

    private Provider<String> readFromVersionCatalog(Settings settings) {
        ProviderFactory providers = settings.getProviders();
        File catalogFile = new File(settings.getSettingsDir(), "gradle/libs.versions.toml");
        return providers.fileContents(getDefaultGradleVersionCatalogFile().fileValue(catalogFile))
                .getAsBytes()
                .map(libsFile -> {
                    try {
                        TomlParseResult toml = Toml.parse(new ByteArrayInputStream(libsFile));
                        TomlTable versions = toml.getTable("versions");
                        if (versions != null) {
                            Object micronaut = versions.get("micronaut");
                            if (micronaut instanceof String micronautVer) {
                                return micronautVer;
                            }
                        }
                        return null;
                    } catch (IOException e) {
                        return null;
                    }
                });
    }
}
