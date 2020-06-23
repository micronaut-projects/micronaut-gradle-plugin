package io.micronaut.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class MicronautLibraryPluginSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile
    File kotlinBuildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
        kotlinBuildFile = testProjectDir.newFile('build.gradle.kts')
    }

    def "test apply defaults for micronaut-library and java"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.0.0.RC1"
                
                processing {
                    incremental true
                }
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble')
                .withPluginClasspath()
                .build()

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
        new File(
                testProjectDir.getRoot(),
                'build/classes/java/main/example/$FooDefinition.class'
        ).exists()
    }

    def "test apply defaults for micronaut-library and groovy"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
                id "groovy"
            }
            
            micronaut {
                version "2.0.0.RC1"
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "groovy", "example")
        def javaFile = testProjectDir.newFile("src/main/groovy/example/Foo.groovy")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble')
                .withPluginClasspath()
                .build()

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        new File(
                testProjectDir.getRoot(),
                'build/classes/groovy/main/example/$FooDefinition.class'
        ).exists()
    }


    def "test apply defaults for micronaut-library and kotlin"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "1.3.72"
                id "org.jetbrains.kotlin.kapt" version "1.3.72"
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.0.0.RC1"
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "kotlin", "example")
        def javaFile = testProjectDir.newFile("src/main/kotlin/example/Foo.kt")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble')
                .withPluginClasspath()
                .build()

        println result.output
        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
    }


    def "test apply defaults for micronaut-library and kotlin with kotlin DSL"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("1.3.72")
                id("org.jetbrains.kotlin.kapt") version("1.3.72")
                id("io.micronaut.library")
            }
            
            micronaut {
                version("2.0.0.RC1")
                processing {
                    incremental(true)
                }
            }
            
            repositories {
                jcenter()
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "kotlin", "example")
        def javaFile = testProjectDir.newFile("src/main/kotlin/example/Foo.kt")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('assemble')
                .withPluginClasspath()
                .build()

        println result.output
        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
    }
}
