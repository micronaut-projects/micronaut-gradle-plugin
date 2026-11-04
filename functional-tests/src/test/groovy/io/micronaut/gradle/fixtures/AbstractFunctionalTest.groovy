package io.micronaut.gradle.fixtures

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.GradleRunner

abstract class AbstractFunctionalTest extends AbstractGradleBuildSpec {
    /**
     * The core with development and test mode, which the launches of mnDev and mnTest run on: micronaut-dev is in
     * Micronaut Core 5.3, published to the snapshots repository until it is released.
     */
    protected static final String DEV_CORE_VERSION = System.getProperty("micronautDevCoreVersion", "5.3.0-SNAPSHOT")

    /**
     * The repository of {@link #DEV_CORE_VERSION}, for the build script of a test that launches.
     */
    protected static final String DEV_CORE_REPOSITORY = """
            repositories {
                maven {
                    url = "https://central.sonatype.com/repository/maven-snapshots/"
                    mavenContent {
                        snapshotsOnly()
                        includeGroup "io.micronaut"
                    }
                }
            }
    """

    @Override
    protected GradleRunner newRunner() {
        GradleRunner.create()
    }

    def setup() {
        patchSettings()
    }

    protected void patchSettings() {
        String version = System.getProperty('project.version')
        settingsFile.text = """
            pluginManagement {
                repositories {
                    ${guardString('mavenLocal()', allowMavenLocal)}
                    mavenCentral()
                    maven {
                        url = "${System.getProperty("internal.plugin.repo")}"
                    }
                    ${guardString('maven { url = "https://s01.oss.sonatype.org/content/repositories/snapshots" }', allowSnapshots)}
                    gradlePluginPortal()
                }
                plugins {
                    id 'io.micronaut.platform.catalog' version '${version}'
                    id 'io.micronaut.minimal.library' version '${version}'
                    id 'io.micronaut.minimal.application' version '${version}'
                    id 'io.micronaut.library' version '${version}'
                    id 'io.micronaut.application' version '${version}'
                    id 'io.micronaut.graalvm' version '${version}'
                    id 'io.micronaut.docker' version '${version}'
                    id 'io.micronaut.aot' version '${version}'
                    id 'io.micronaut.openapi' version '${version}'
                    id 'io.micronaut.test-resources' version '${version}'
                    id 'io.micronaut.configuration.validation' version '${version}'
                }
            }
        """ + (settingsFile.exists() ? settingsFile.text : "")
    }
}
