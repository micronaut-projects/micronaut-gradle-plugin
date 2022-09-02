package io.micronaut.gradle.crac

import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
class CracCustomizationSpec extends BaseCracGradleBuildSpec {

    void "default warmup script is used by default"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/checkpoint/warmup.sh") == CracCustomizationSpec.getResourceAsStream("/warmup.sh").text
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
        fileTextContents("build/docker/main/checkpoint/checkpoint.sh") == CracCustomizationSpec.getResourceAsStream("/checkpoint.sh").text
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

    void "base image default is as expected"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile").readLines().head() == "FROM --platform=linux/amd64 $MicronautCRaCPlugin.CRAC_DEFAULT_BASE_IMAGE"
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").readLines().head() == "FROM --platform=linux/amd64 $MicronautCRaCPlugin.CRAC_DEFAULT_BASE_IMAGE"
    }

    void "base image is customizable"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlock(getMicronautConfigBlock("""crac {
    baseImage.set("timyates:latest")
}"""))

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile").readLines().head() == "FROM --platform=linux/amd64 timyates:latest"
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").readLines().head() == "FROM --platform=linux/amd64 timyates:latest"
    }

    void "platform can be removed"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlock(getMicronautConfigBlock("""crac {
    platform.set("")
    baseImage.set("timyates:latest")
}"""))

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile").readLines().head() == "FROM timyates:latest"
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").readLines().head() == "FROM timyates:latest"
    }

    void "platform can be customized"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlock(getMicronautConfigBlock("""crac {
    platform.set("raspberry-pi/arm64")
    baseImage.set("timyates:latest")
}"""))

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile").readLines().head() == "FROM --platform=raspberry-pi/arm64 timyates:latest"
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").readLines().head() == "FROM --platform=raspberry-pi/arm64 timyates:latest"
    }
}
