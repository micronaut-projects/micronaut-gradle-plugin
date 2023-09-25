package io.micronaut.gradle.crac

import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
class CracCustomizationSpec extends BaseCracGradleBuildSpec {

    void "run script is created"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/scripts/run.sh") == CracCustomizationSpec.getResourceAsStream("/run.sh").text
    }

    void "default warmup script is used by default"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/scripts/warmup.sh") == CracCustomizationSpec.getResourceAsStream("/warmup.sh").text
    }

    void "warmup script is customizable"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock("""crac {
    warmupScript.set(project.layout.projectDirectory.dir("src").file("test.sh"))
}"""))
        writeFile("src/test.sh", "This is a test")

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/scripts/warmup.sh") == "This is a test"
    }

    void "default checkpoint script is used by default"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock
        def expected = CracCustomizationSpec.getResourceAsStream("/checkpoint.sh").text.replace("@READINESS@", MicronautCRaCPlugin.CRAC_DEFAULT_READINESS_COMMAND)

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/scripts/checkpoint.sh") == expected
    }

    void "checkpoint script is customizable"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock("""crac {
    checkpointScript.set(project.layout.projectDirectory.dir("src").file("test.sh"))
}"""))
        writeFile("src/test.sh", "And another test")

        when:
        def result = build('checkpointScripts', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/scripts/checkpoint.sh") == "And another test\n"
    }

    void "base image default is as expected"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile").readLines().head() == "FROM $MicronautCRaCPlugin.CRAC_DEFAULT_BASE_IMAGE"
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").readLines().head() == "FROM $MicronautCRaCPlugin.CRAC_DEFAULT_BASE_IMAGE"
    }

    void "base image is customizable"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock("""crac {
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
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock("""crac {
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

    void "Arch defaults to OS arch, and java version defaults to 17"() {
        given:
        def javaVersion = "17"
        def expectedArch = System.properties['os.arch'] == MicronautCRaCPlugin.ARM_ARCH ? MicronautCRaCPlugin.ARM_ARCH : MicronautCRaCPlugin.X86_64_ARCH
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock())

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").contains("https://api.azul.com/metadata/v1/zulu/packages/?java_version=$javaVersion&arch=$expectedArch&crac_supported=true&java_package_type=jdk&latest=true&release_status=ga&certifications=tck&page=1&page_size=100")
    }

    void "Azul CRaC JDK and arch can be changed"() {
        given:
        def javaVersion = "21"
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock("""crac {
    javaVersion.set(JavaLanguageVersion.of($javaVersion))
    arch.set('$MicronautCRaCPlugin.ARM_ARCH')
}"""))

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").contains("https://api.azul.com/metadata/v1/zulu/packages/?java_version=$javaVersion&arch=$MicronautCRaCPlugin.ARM_ARCH&crac_supported=true&java_package_type=jdk&latest=true&release_status=ga&certifications=tck&page=1&page_size=100")
    }

    void "Weird java versions cause an error"() {
        given:
        def javaVersion = "tim"
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock("""crac {
    javaVersion.set(JavaLanguageVersion.of("$javaVersion"))
}"""))

        when:
        def result = fails('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains('Caused by: java.lang.NumberFormatException: For input string: "tim"')
    }

    void "dockerfiles can be customized"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        baseDir.resolve("DockerfileCracCheckpoint").write("I am checkpoint dockerfile")
        baseDir.resolve("Dockerfile").write("I am the main dockerfile")

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").readLines().head() == "I am checkpoint dockerfile"
        fileTextContents("build/docker/main/Dockerfile").readLines().head() == "I am the main dockerfile"
    }
}
