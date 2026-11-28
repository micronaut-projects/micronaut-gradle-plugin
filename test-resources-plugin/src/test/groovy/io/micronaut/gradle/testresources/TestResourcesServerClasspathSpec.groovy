package io.micronaut.gradle.testresources

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.testresources.buildtools.VersionInfo
import org.gradle.testkit.runner.TaskOutcome

class TestResourcesServerClasspathSpec extends AbstractGradleBuildSpec {

    def "starts Test Resources using #serverArtifact"(boolean controlPanel, String serverArtifact) {
        given:
        def testResourcesVersion = VersionInfo.getVersion()
        settingsFile << "rootProject.name = 'test-resources-server-classpath'\n"
        buildFile << """
            plugins {
                id 'io.micronaut.test-resources'
            }

            $repositoriesBlock

            micronaut {
                version = '$micronautVersion'
                testResources {
                    inferClasspath = false
                }
            }

            ${controlPanel ? "dependencies { testResourcesService 'io.micronaut.testresources:micronaut-test-resources-control-panel:$testResourcesVersion' }" : ''}

            tasks.named('internalStartTestResourcesService') {
                doFirst { task ->
                    def servers = task.classpath.files*.name.findAll {
                        it.startsWith('micronaut-test-resources-server-')
                    }
                    assert servers == ['${serverArtifact}-${testResourcesVersion}.jar']
                }
            }
        """

        when:
        def result = configureRunner('internalStartTestResourcesService', '--configuration-cache')
                .withDebug(false)
                .build()

        then:
        result.task(':internalStartTestResourcesService').outcome == TaskOutcome.SUCCESS
        result.output.contains('A Micronaut Test Resources server is listening on port')
        !controlPanel || result.output.contains('Test Resources control panel can be browsed at')

        where:
        controlPanel | serverArtifact
        true         | 'micronaut-test-resources-server-thin'
        false        | 'micronaut-test-resources-server'
    }
}
