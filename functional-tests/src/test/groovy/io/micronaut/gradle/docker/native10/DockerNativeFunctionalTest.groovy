package io.micronaut.gradle.docker.native10

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable })
@IgnoreIf({ os.windows })
class DockerNativeFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/373")
    def "docker plugin shouldn't assume nativeCompile is available"() {
        settingsFile << """
            rootProject.name = 'hello-world'
        """
        buildFile << """plugins {
  id 'io.micronaut.minimal.application'
  id 'io.micronaut.docker'
}

micronaut {
    version "$micronautVersion"
    runtime "netty"
}
            
$repositoriesBlock

mainClassName="example.Application"

// Force realization of all tasks to trigger the problem
afterEvaluate {
    project.tasks.forEach(task -> println(task.getName()))
}
"""

        when:
        def result = build('dockerfile')

        def task = result.task(":dockerfile")

        then:
        task.outcome == TaskOutcome.SUCCESS
    }

}
