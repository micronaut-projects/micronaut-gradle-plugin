package io.micronaut.gradle


import org.gradle.testkit.runner.TaskOutcome

class BuildLayersSpec extends AbstractGradleBuildSpec {

    void 'test build layers with custom build directory'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.4.2"
                runtime "netty"
                testRuntime "junit5"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }
            mainClassName="example.Application"
            
            project.buildDir = "build-custom"
        """

        when:
        def result = build('buildLayers')

        def task = result.task(":buildLayers")

        then:
        task.outcome == TaskOutcome.SUCCESS
        new File(testProjectDir.root, "build-custom/docker/layers").exists()
        !new File(testProjectDir.root, "build/docker/layers").exists()
    }
}
