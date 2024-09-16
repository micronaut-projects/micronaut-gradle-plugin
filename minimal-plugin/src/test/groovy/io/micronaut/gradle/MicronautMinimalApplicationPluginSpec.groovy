package io.micronaut.gradle


import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

class MicronautMinimalApplicationPluginSpec extends AbstractGradleBuildSpec {

    def "test junit 5 test runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """
        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()
        when:
        def result = build('test')

        def task = result.task(":test")
        println result.output

        then:
        task.outcome == TaskOutcome.SUCCESS
        testProjectDir.root.toPath()
                .resolve('build/classes/java/test/example/$ExampleTest$Definition.class').toFile().exists()

    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/292")
    def "Groovy sources are found when configuring watch paths"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id 'groovy'
            }

            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }

            $repositoriesBlock

            dependencies {
                implementation "org.apache.groovy:groovy"
            }
            $withSerde

            mainClassName="example.Application"
        """

        testProjectDir.newFolder("src", "main", "groovy", "example")
        def groovyApp = testProjectDir.newFile("src/main/groovy/example/Application.groovy")

        groovyApp << """package example

            println "Watch paths: \${System.getProperty('micronaut.io.watch.paths')}"
        """

        when:
        def result = build('run', "-D${MicronautMinimalApplicationPlugin.INTERNAL_CONTINUOUS_FLAG}=true")
        def task = result.task(":run")
        def output = result.output.readLines()
        def watchLine = output.find { it.startsWith("Watch paths: ") }
                .replace(File.separatorChar, (char) '/')

        then:
        task.outcome == TaskOutcome.SUCCESS
        watchLine.contains 'src/main/groovy'
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/594")
    def "can detect that SnakeYAML is missing from classpath"() {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"
            configurations.all {
                exclude module: 'snakeyaml'
            }

        """
        testProjectDir.newFolder("src", "test", "java", "example")
        writeExampleClass()
        testProjectDir.newFolder("src", "main", "resources")
        testProjectDir.newFile("src/main/resources/application.yml") << """
            micronaut:
              application:
                name: hello-world
        """
        when:
        def result = fails('test')

        then:
        result.output.contains('YAML configuration file detected but snakeyaml is not on classpath. Make sure to add a runtimeOnly dependency on snakeyaml, e.g \'runtimeOnly("org.yaml:snakeyaml")\'')
    }

    private File writeExampleClass() {
        def javaFile = testProjectDir.newFile("src/test/java/example/ExampleTest.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import jakarta.inject.Inject;

@MicronautTest
public class ExampleTest {

    @Inject
    EmbeddedApplication application;

    @Test
    void testItWorks() {
        Assertions.assertTrue(application.isRunning());
    }

}
"""
        javaFile
    }

    def "can override the default Micronaut core version via the DSL"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
 
                // Gradle won't support using the `=` operator here
                coreVersion.set("2048")
                
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """

        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()

        when:
        def result = fails('test')

        then:
        result.output.contains('Could not find io.micronaut:micronaut-inject:2048')

    }

    def "can override the default Micronaut core version via the Micronaut version catalog"() {
        given:
        settingsFile << """
            rootProject.name = 'hello-world'
            dependencyResolutionManagement {
                repositories {
                    $repositoriesBlock
                }
                versionCatalogs {
                    mn {
                        // simulates what the Micronaut settings plugin would do
                        // because we can't use it directly in this test
                        from("io.micronaut.platform:micronaut-platform:${micronautVersion}")
                        
                        // override micronaut core version
                        version("micronaut.core", "2048")
                    }
                }
            }
            """
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
                
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """

        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()

        when:
        def result = fails('test')

        then:
        result.output.contains('Could not find io.micronaut:micronaut-inject:2048')

    }

    def "can override the default Micronaut core version via a Gradle property"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """
        file("gradle.properties") << "micronautCoreVersion=2048"

        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()

        when:
        def result = fails('test')

        then:
        result.output.contains('Could not find io.micronaut:micronaut-inject:2048')

    }

    def "can override the default Micronaut Netty version via the DSL"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
 
                // Gradle won't support using the `=` operator here
                httpNettyVersion.set("2048")
                
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """

        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()

        when:
        def result = fails('test')

        then:
        result.output.contains('Could not find io.micronaut:micronaut-http-server-netty:2048')

    }

    def "can override the default Micronaut Netty version via a Gradle property"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """
        file("gradle.properties") << "micronautHttpNettyVersion=2048"

        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()

        when:
        def result = fails('test')

        then:
        result.output.contains('Could not find io.micronaut:micronaut-http-server-netty:2048')

    }

    def "can ignore an automatic dependency"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
                ignoredAutomaticDependencies.add("io.micronaut:micronaut-inject-java")
            }
            
            $repositoriesBlock
            mainClassName="example.Application"
        """
        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()
        when:
        def result = build('test')

        def task = result.task(":test")
        println result.output

        then:
        task.outcome == TaskOutcome.FAILED
        !testProjectDir.root.toPath()
                .resolve('build/classes/java/test/example/$ExampleTest$Definition.class').toFile().exists()
    }
}
