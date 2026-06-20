package io.micronaut.gradle.settings

import io.micronaut.gradle.fixtures.AbstractFunctionalTest

class BuildSrcCatalogFunctionalTest extends AbstractFunctionalTest {
    def "can resolve the Micronaut catalog from the owning build version catalog when applied in buildSrc"() {
        given:
        writeBuildSrcCatalogBuild()
        file('gradle').mkdirs()
        file('gradle/libs.versions.toml') << """
[versions]
micronaut = "$micronautVersion"
"""

        when:
        def result = buildBuildSrc('resolveCatalog')

        then:
        result.output.contains('micronaut-inject-java')
    }

    def "can use the owning build gradle properties and override file when applied in buildSrc"() {
        given:
        writeBuildSrcCatalogBuild()
        file('gradle.properties') << """
micronautVersion=$micronautVersion
"""
        file('gradle').mkdirs()
        file('gradle/mn-override.versions.toml') << """
[versions]
micronaut-core = "2048"
"""

        when:
        def result = failsBuildSrc('resolveCatalog')

        then:
        result.output.contains('Could not find io.micronaut:micronaut-inject-java:2048')
    }

    def "buildSrc local catalog inputs win over the owning build fallback"() {
        given:
        writeBuildSrcCatalogBuild()
        file('gradle.properties') << """
micronautVersion=$micronautVersion
"""
        file('buildSrc/gradle.properties') << """
micronautVersion=2048
"""

        when:
        def result = failsBuildSrc('resolveCatalog')

        then:
        result.output.contains('Could not find io.micronaut.platform:micronaut-platform:2048')
    }

    private void writeBuildSrcCatalogBuild() {
        file('buildSrc').mkdirs()
        file('buildSrc/settings.gradle') << """
            pluginManagement {
                repositories {
                    mavenCentral()
                    maven {
                        url = "${System.getProperty("internal.plugin.repo")}"
                    }
                    gradlePluginPortal()
                }
                plugins {
                    id 'io.micronaut.platform.catalog' version '${System.getProperty('project.version')}'
                }
            }

            plugins {
                id 'io.micronaut.platform.catalog'
            }

            rootProject.name = 'buildSrc'
        """
        file('buildSrc/build.gradle') << """
            plugins {
                id 'groovy-gradle-plugin'
            }

            repositories {
                mavenCentral()
            }

            configurations {
                verify
            }

            dependencies {
                verify mn.micronaut.inject.java
            }

            tasks.register('resolveCatalog') {
                doLast {
                    println configurations.verify.files*.name.sort().join(',')
                }
            }
        """
    }

    private def buildBuildSrc(String... args) {
        withBuildSrcBaseDir {
            build(args)
        }
    }

    private def failsBuildSrc(String... args) {
        withBuildSrcBaseDir {
            fails(args)
        }
    }

    private def withBuildSrcBaseDir(Closure action) {
        def originalBaseDir = baseDir
        baseDir = file('buildSrc').toPath()
        try {
            action.call()
        } finally {
            baseDir = originalBaseDir
        }
    }
}
