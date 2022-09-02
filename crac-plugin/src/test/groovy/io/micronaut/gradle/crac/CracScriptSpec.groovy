package io.micronaut.gradle.crac

import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
class CracScriptSpec extends BaseCracGradleBuildSpec {

    void "default warmup script is used by default"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/checkpoint/warmup.sh") == CracScriptSpec.getResourceAsStream("/warmup.sh").text
    }

    void "warmup script is customizable"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlock(getMicronautConfigBlock("""crac {
    warmupScript.set(project.layout.projectDirectory.dir("src").file("test.sh"))
}"""))
        writeFile("src/test.sh", "This is a test")

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/checkpoint/warmup.sh") == "This is a test"
    }

    void "default checkpoint script is used by default"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/checkpoint/checkpoint.sh") == CracScriptSpec.getResourceAsStream("/checkpoint.sh").text
    }

    void "checkpoint script is customizable"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlock(getMicronautConfigBlock("""crac {
    checkpointScript.set(project.layout.projectDirectory.dir("src").file("test.sh"))
}"""))
        writeFile("src/test.sh", "And another test")

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/checkpoint/checkpoint.sh") == "And another test"
    }
}
