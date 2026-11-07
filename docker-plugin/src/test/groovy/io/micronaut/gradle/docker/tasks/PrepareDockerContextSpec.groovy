package io.micronaut.gradle.docker.tasks

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class PrepareDockerContextSpec extends Specification {

    @TempDir
    File projectDir

    def "output is an exact snapshot of existing input directories"() {
        given:
        def retainedInput = new File(projectDir, 'inputs/retained')
        def removedInput = new File(projectDir, 'inputs/removed')
        new File(retainedInput, 'reachability-metadata.json').tap {
            parentFile.mkdirs()
            text = 'retained'
        }
        new File(removedInput, 'reachability-metadata.json').tap {
            parentFile.mkdirs()
            text = 'removed'
        }
        def output = new File(projectDir, 'build/docker/native-main/config-dirs')
        def task = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
                .tasks
                .create('prepareDockerContext', PrepareDockerContext)
        task.inputDirectories.from(retainedInput, removedInput)
        task.outputDirectory.set(output)

        when:
        task.copy()

        then:
        new File(output, 'retained/reachability-metadata.json').text == 'retained'
        new File(output, 'removed/reachability-metadata.json').text == 'removed'

        when:
        assert removedInput.deleteDir()
        def outsideOutput = new File(output.parentFile, 'keep.txt')
        outsideOutput.text = 'keep'
        task.copy()

        then:
        new File(output, 'retained/reachability-metadata.json').text == 'retained'
        !new File(output, 'removed').exists()
        outsideOutput.text == 'keep'
    }
}
