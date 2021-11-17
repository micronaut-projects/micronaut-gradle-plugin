package io.micronaut.gradle


import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Requires

class DockerfileSpec extends AbstractGradleBuildSpec {
    @Requires({ AbstractGradleBuildSpec.graalVmAvailable })
    @IgnoreIf({ os.isWindows() })
    void 'test build native docker file'() {
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
            
            $repositoriesBlock
            
            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }
            mainClassName="example.Application"
            
        """

        when:
        def result = build('javaToolchains', 'dockerfileNative')

        def task = result.task(":dockerfileNative")
        println(result.output)
        
        then:
        task.outcome == TaskOutcome.SUCCESS
        result.output.contains("Dockerfile written to")
        result.output.contains("build/docker/main/DockerfileNative")

        new File("$testProjectDir.root/build/docker/main/DockerfileNative").text.count("-cp") == 1
    }
}
