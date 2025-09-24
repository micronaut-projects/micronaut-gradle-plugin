package io.micronaut.gradle.openrewrite

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class MicronautOpenRewritePluginTest extends Specification{

    @TempDir
    File testProjectDir

    File buildFile
    File settingsFile

    def setup() {
        buildFile = new File(testProjectDir, "build.gradle")
        settingsFile = new File(testProjectDir, "settings.gradle")
        settingsFile << "rootProject.name = 'rewrite-test'"
    }

    def "plugin applies successfully"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'io.micronaut.minimal.application'
                id 'io.micronaut.openrewrite'
            }
            
            micronaut {
                version "4.8.2"
            }

            repositories {
                mavenCentral()
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .withPluginClasspath()
                .build()

        then:
        result.task(":tasks").outcome == TaskOutcome.SUCCESS
        result.output.contains("rewriteRun")
        result.output.contains("rewriteDryRun")
    }

    def "extension is created with default values"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'io.micronaut.minimal.application'
                id 'io.micronaut.openrewrite'
            }
            
            micronaut {
                version "4.8.2"
            }

            repositories {
                mavenCentral()
            }
            
            task printExtensionValues {
                doLast {
                    println "AddDefaultRecipes: \${micronaut.openrewrite.addDefaultRecipes.get()}"
                    println "Version: \${micronaut.openrewrite.version.get()}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("printExtensionValues")
                .withPluginClasspath()
                .build()

        then:
        result.task(":printExtensionValues").outcome == TaskOutcome.SUCCESS
        result.output.contains("AddDefaultRecipes: true")
        result.output.contains("Version: +")
    }

    def "extension allows custom configuration"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'io.micronaut.minimal.application'
                id 'io.micronaut.openrewrite'
            }
            
            micronaut {
                version "4.8.2"
                openrewrite {
                    addDefaultRecipes = false
                    version = "1.2.3"
                }
            }

            repositories {
                mavenCentral()
            }
            
            task printExtensionValues {
                doLast {
                    println "AddDefaultRecipes: \${micronaut.openrewrite.addDefaultRecipes.get()}"
                    println "Version: \${micronaut.openrewrite.version.get()}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("printExtensionValues")
                .withPluginClasspath()
                .build()

        then:
        result.task(":printExtensionValues").outcome == TaskOutcome.SUCCESS
        result.output.contains("AddDefaultRecipes: false")
        result.output.contains("Version: 1.2.3")
    }

    def "rewrite configuration gets dependencies when enabled"() {
        given:
        buildFile << """
            plugins {
                id 'io.micronaut.minimal.application'
                id 'io.micronaut.openrewrite'
            }
            
            micronaut {
                version "4.8.2"
                openrewrite {
                    version = "1.0.0"
                }
            }

            repositories {
                mavenCentral()
            }
            
            task printRewriteDependencies {
                doLast {
                    configurations.rewrite.dependencies.each { dep ->
                        println "Rewrite dependency: \${dep.group}:\${dep.name}:\${dep.version}"
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("printRewriteDependencies")
                .withPluginClasspath()
                .build()

        then:
        result.task(":printRewriteDependencies").outcome == TaskOutcome.SUCCESS
        result.output.contains("Rewrite dependency: ${MicronautOpenRewritePlugin.RECIPE_GA_COORDINATES}:1.0.0")
    }

    def "rewrite configuration gets no dependencies when disabled"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'io.micronaut.minimal.application'
                id 'io.micronaut.openrewrite'
            }
            
            micronaut {
                version "4.8.2"
                openrewrite {
                    addDefaultRecipes = false
                    version = "1.0.0"
                }
            }

            repositories {
                mavenCentral()
            }
            
            task printRewriteDependencies {
                doLast {
                    def micronautDeps = configurations.rewrite.dependencies
                    println "Micronaut rewrite dependencies count: \${micronautDeps.size()}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("printRewriteDependencies")
                .withPluginClasspath()
                .build()

        then:
        result.task(":printRewriteDependencies").outcome == TaskOutcome.SUCCESS
        result.output.contains("Micronaut rewrite dependencies count: 0")
    }

    def "works with latest version placeholder"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'io.micronaut.minimal.application'
                id 'io.micronaut.openrewrite'
            }
            
            micronaut {
                version "4.8.2"
            }

            repositories {
                mavenCentral()
            }
            
            task printRewriteDependencies {
                doLast {
                    configurations.rewrite.dependencies.each { dep ->
                        println "Rewrite dependency: \${dep.group}:\${dep.name}:\${dep.version}"
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("printRewriteDependencies")
                .withPluginClasspath()
                .build()

        then:
        result.task(":printRewriteDependencies").outcome == TaskOutcome.SUCCESS
        result.output.contains("Rewrite dependency: ${MicronautOpenRewritePlugin.RECIPE_GA_COORDINATES}:+")
    }
}
