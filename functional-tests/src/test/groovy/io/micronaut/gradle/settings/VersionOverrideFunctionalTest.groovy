package io.micronaut.gradle.settings

import io.micronaut.gradle.fixtures.AbstractFunctionalTest

class VersionOverrideFunctionalTest extends AbstractFunctionalTest {
    def "can override the default Micronaut core version via the Micronaut version catalog"() {
        given:
        settingsFile << """
            plugins {
                id 'io.micronaut.platform.catalog'
            }

            rootProject.name = 'hello-world'            
            """
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
            }
            
            micronaut {                
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            $withSerde
        """
        file("gradle.properties") << """
            micronautVersion=$micronautVersion
        """

        file('gradle').mkdir()
        file('gradle/mn.versions.toml') << """
[versions]
micronaut-core = "2048"
"""

        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()

        when:
        def result = fails('test')

        then:
        result.output.contains('Could not find io.micronaut:micronaut-inject:2048')

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
}
