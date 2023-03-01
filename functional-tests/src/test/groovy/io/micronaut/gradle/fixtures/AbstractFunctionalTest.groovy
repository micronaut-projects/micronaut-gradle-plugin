package io.micronaut.gradle.fixtures

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.GradleRunner

abstract class AbstractFunctionalTest extends AbstractGradleBuildSpec {
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
                    id 'io.micronaut.minimal.library' version '${version}'
                    id 'io.micronaut.minimal.application' version '${version}'
                    id 'io.micronaut.library' version '${version}'
                    id 'io.micronaut.application' version '${version}'
                    id 'io.micronaut.graalvm' version '${version}'
                    id 'io.micronaut.docker' version '${version}'
                    id 'io.micronaut.aot' version '${version}'
                    id 'io.micronaut.test-resources' version '${version}'
                }
            }
        """ + (settingsFile.exists() ? settingsFile.text : "")
    }
}
