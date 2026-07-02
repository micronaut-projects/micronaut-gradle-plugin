package io.micronaut.gradle.crac

import groovy.json.JsonSlurper
import spock.lang.IgnoreIf

class CracCustomizationSpec extends BaseCracGradleBuildSpec {

    @IgnoreIf({ os.windows })
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

    @IgnoreIf({ os.windows })
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

    @IgnoreIf({ os.windows })
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

    @IgnoreIf({ os.windows })
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

    @IgnoreIf({ os.windows })
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

    void "Arch defaults to OS arch, and java version defaults to 25"() {
        given:
        def javaVersion = "25"
        def expectedArch = System.properties['os.arch'] == MicronautCRaCPlugin.ARM_ARCH ? MicronautCRaCPlugin.ARM_ARCH : MicronautCRaCPlugin.X86_64_ARCH
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock())

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')
        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").contains("https://api.azul.com/metadata/v1/zulu/packages/?java_version=$javaVersion&os=$MicronautCRaCPlugin.DEFAULT_OS&arch=$expectedArch&crac_supported=true&java_package_type=jdk&latest=true&release_status=ga&certifications=tck&archive_type=tar.gz&page=1&page_size=100")
    }

    void "default CRaC URL returns a single JDK"() {
        when:
        def expectedArch = System.properties['os.arch'] == MicronautCRaCPlugin.ARM_ARCH ? MicronautCRaCPlugin.ARM_ARCH : MicronautCRaCPlugin.X86_64_ARCH
        def json = new URL(CRaCCheckpointDockerfile.getUrl("25", MicronautCRaCPlugin.DEFAULT_OS, expectedArch)).text
        def result = new JsonSlurper().parseText(json)

        then:
        result.size() == 1
    }

    void "Azul CRaC JDK os and arch can be changed"() {
        given:
        def javaVersion = "21"
        def configuredOs = "configured-os"
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock("""crac {
    javaVersion.set(JavaLanguageVersion.of($javaVersion))
    arch.set('$MicronautCRaCPlugin.ARM_ARCH')
    os.set('$configuredOs')
}"""))

        when:
        def result = build('dockerfileCrac', 'checkpointDockerfile', '-s')

        then:
        result.output.contains("BUILD SUCCESSFUL")
        fileTextContents("build/docker/main/Dockerfile.CRaCCheckpoint").contains("https://api.azul.com/metadata/v1/zulu/packages/?java_version=$javaVersion&os=$configuredOs&arch=$MicronautCRaCPlugin.ARM_ARCH&crac_supported=true&java_package_type=jdk&latest=true&release_status=ga&certifications=tck&archive_type=tar.gz&page=1&page_size=100")
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
        result.output.contains("Caused by: java.lang.IllegalArgumentException: JavaLanguageVersion must be a positive integer, not 'tim'")
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

    void "restore verification tasks are registered and wired with default CRaC restore settings"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock

        when:
        def result = build('tasks', '--all')

        then:
        result.output.contains('dockerVerifyCrac - Verifies the restored CRaC Docker Image')
        result.output.contains('postRestoreCreateContainer - Creates the CRaC restored-image verification container')
        result.output.contains('postRestoreDockerRun - Runs the CRaC restored-image verification container')
        result.output.contains('postRestoreLogs - Shows logs from the CRaC restored-image verification container')
        result.output.contains('postRestoreRemoveContainer - Removes the CRaC restored-image verification container')
    }

    void "restore verification task uses configurable readiness and docker runtime settings"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlockWithMicronautConfig(getMicronautConfigBlock("""crac {
    preCheckpointReadinessCommand.set("curl --fail http://localhost:8080/health")
    postRestoreReadinessCommand.set("curl --fail http://localhost:8080/ready")
    postRestoreReadinessTimeout.set(java.time.Duration.ofSeconds(12))
    postRestoreReadinessRetryDelay.set(java.time.Duration.ofSeconds(2))
    postRestoreNetwork.set("ci-network")
    postRestorePortBindings.add("18080:8080")
    postRestoreEnvironment.put("MICRONAUT_ENVIRONMENTS", "test")
    postRestoreArgs.add("--micronaut.server.port=8080")
    retainPostRestoreContainer.set(true)
}"""))
        buildFile << """

tasks.register("printCracRestoreVerification") {
    doLast {
        def create = tasks.named("postRestoreCreateContainer").get()
        def verify = tasks.named("dockerVerifyCrac").get()
        def remove = tasks.named("postRestoreRemoveContainer").get()
        println("verify.commands=" + verify.commands.get().collect { it.join(' ') })
        println("verify.probe=" + verify.execProbe.pollTime + ":" + verify.execProbe.pollInterval)
        println("create.network=" + create.hostConfig.network.get())
        println("create.ports=" + create.hostConfig.portBindings.get())
        println("create.envKeys=" + create.envVars.get().keySet())
        println("create.cmd=" + create.cmd.get())
        println("create.capAdd=" + create.hostConfig.capAdd.get())
        println("remove.onlyIf=" + remove.onlyIf.isSatisfiedBy(remove))
    }
}
"""

        when:
        def result = build('printCracRestoreVerification')

        then:
        result.output.contains('verify.commands=[/bin/sh -c curl --fail http://localhost:8080/ready]')
        result.output.contains('verify.probe=12000:2000')
        result.output.contains('create.network=ci-network')
        result.output.contains('create.ports=[18080:8080]')
        result.output.contains('create.envKeys=[MICRONAUT_ENVIRONMENTS]')
        result.output.contains('create.cmd=[--micronaut.server.port=8080]')
        result.output.contains('create.capAdd=[SYS_PTRACE]')
        result.output.contains('remove.onlyIf=false')
    }

    void "restore verification failure and cleanup behavior is wired"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << buildFileBlock
        buildFile << """

tasks.register("printCracRestoreFailureWiring") {
    doLast {
        def start = tasks.named("postRestoreDockerRun").get()
        def verify = tasks.named("dockerVerifyCrac").get()
        def remove = tasks.named("postRestoreRemoveContainer").get()
        println("verify.successCodes=" + verify.successOnExitCodes.get())
        println("verify.probe=" + verify.execProbe.pollTime + ":" + verify.execProbe.pollInterval)
        println("verify.finalizedBy=" + verify.finalizedBy.getDependencies(verify).collect { it.name }.sort())
        println("start.finalizedBy=" + start.finalizedBy.getDependencies(start).collect { it.name }.sort())
        println("remove.onlyIf=" + remove.onlyIf.isSatisfiedBy(remove))
    }
}
"""

        when:
        def result = build('printCracRestoreFailureWiring')

        then:
        result.output.contains('verify.successCodes=[0]')
        result.output.contains('verify.probe=120000:2000')
        result.output.contains('verify.finalizedBy=[postRestoreLogs, postRestoreRemoveContainer]')
        result.output.contains('start.finalizedBy=[postRestoreLogs, postRestoreRemoveContainer]')
        result.output.contains('remove.onlyIf=true')
    }
}
