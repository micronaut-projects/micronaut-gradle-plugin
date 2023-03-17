package io.micronaut.gradle.docker.native12

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable })
@IgnoreIf({ os.windows })
class DockerNativeFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/667")
    def "dockerfile tweaks participate in up-to-date checking"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }

            mainClassName="example.Application"

            tasks.withType(io.micronaut.gradle.docker.DockerBuildOptions).configureEach {
                editDockerfile {
                    after('COPY layers/libs /home/app/libs') {
                        insert('COPY server.iprof /home/app/server.iprof')
                    } 
                }
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

class Application {
    public static void main(String... args) {
    
    }
}
"""

        when:
        def result = build('dockerfile', '-s')

        then:
        def dockerfile = new File(testProjectDir.root, 'build/docker/main/Dockerfile').text
        dockerfile == """FROM openjdk:17-alpine
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY server.iprof /home/app/server.iprof
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

        when:
        result = build('dockerfile', '-s')

        then:
        result.task(":dockerfile").outcome == TaskOutcome.UP_TO_DATE

        when:
        buildFile << """
            tasks.withType(io.micronaut.gradle.docker.DockerBuildOptions).configureEach {
                editDockerfile {
                    after('COPY server.iprof /home/app/server.iprof') {
                        insert('COPY README.TXT /home/app/README.TXT')
                    } 
                }
            }
        """
        result = build('dockerfile', '-s')

        then:
        result.task(":dockerfile").outcome == TaskOutcome.SUCCESS

    }


}
