package io.micronaut.gradle

import io.micronaut.gradle.docker.DockerBuildTask
import io.micronaut.gradle.docker.DockerSettings
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DockerBuildTaskSpec extends Specification {


    void "test docker build task command line"() {
        when:
        def project = ProjectBuilder.builder()
                .build()
        project.plugins.apply(MicronautApplicationPlugin)
        DockerBuildTask task = project.tasks.getByName("buildDockerImage")
        String commandLine = task.getCommandLine().join(" ")

        then:
        commandLine == 'java -Xmx128m -jar /home/app/application.jar'
    }

    void "test docker build configure"() {
        when:
        def project = ProjectBuilder.builder()
                .build()
        project.plugins.apply(MicronautApplicationPlugin)
        project.extensions.configure(MicronautExtension, { MicronautExtension ext ->
            ext.docker({ DockerSettings settings ->
                settings.from("scratch")
                settings.tag("something")
                settings.port(8090)
            })
        })
        project.tasks.withType(DockerBuildTask) { DockerBuildTask task ->
            task.setMaxHeapSize("256m")
            task.systemProperties(foo:'bar')
            task.jvmArgs("-verbose")
        }
        DockerBuildTask task = project.tasks.getByName("buildDockerImage")
        String commandLine = task.getCommandLine().join(" ")

        then:
        commandLine == 'java -Dfoo="bar" -Xmx256m -verbose -jar /home/app/application.jar'
        task.port.get() == 8090
        task.baseImage.get() == 'scratch'
        task.tag.get() == 'something'
    }
}
