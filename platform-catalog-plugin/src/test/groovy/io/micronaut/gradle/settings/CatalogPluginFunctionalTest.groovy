package io.micronaut.gradle.settings

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome

class CatalogPluginFunctionalTest extends AbstractGradleBuildSpec {
    def "can use the Micronaut version catalog"() {
        given:
        settingsFile << """
            plugins {
                id 'io.micronaut.platform.catalog'
            }

            rootProject.name = 'hello-world'            
            """
        buildFile << """
            plugins {
               id 'java'
            }
            
            dependencies {
                annotationProcessor(mn.micronaut.inject.java)
                implementation(platform(mn.micronaut.platform))
                implementation(mn.micronaut.runtime)
                
                testAnnotationProcessor(mn.micronaut.inject.java)
                testImplementation(mn.micronaut.test.junit5)
                testRuntimeOnly(mn.micronaut.serde.jackson)
                testRuntimeOnly(mn.micronaut.http.server.netty)
                // Should probably be managed in Micronaut Test
                testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
            }
            
            tasks.withType(Test) {
                useJUnitPlatform()
            }
        """
        file("gradle.properties") << """
            micronautVersion=$micronautVersion
        """

        file('gradle').mkdir()
        file('gradle/libs.versions.toml') << """
[versions]
micronaut = "$micronautVersion"
"""

        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = writeExampleClass()
        when:
        def result = build('test')

        def task = result.task(":test")
        println result.output

        then:
        result.output.contains('Creating bean classes for 1 type elements')
        task.outcome == TaskOutcome.SUCCESS

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
