package io.micronaut.gradle.buildpacks

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
class MicronautBuildpacksPluginSpec extends AbstractGradleBuildSpec {

    def "registers buildpack extension and tasks"() {
        given:
        writeBasicProject()
        buildFile << """
            tasks.register("verifyBuildpacks") {
                doLast {
                    assert micronaut.extensions.buildpacks.imageName.get() == "hello-world:latest"
                    assert micronaut.extensions.buildpacks.builder.get() == "paketobuildpacks/builder-jammy-base"
                    assert tasks.findByName("prepareBuildpackApplication") != null
                    assert tasks.findByName("buildBuildpackImage") != null
                    assert tasks.findByName("publishBuildpackImage") != null
                    assert tasks.findByName("buildNativeBuildpackImage") != null
                    assert tasks.findByName("publishNativeBuildpackImage") != null
                }
            }
        """

        when:
        def result = build("verifyBuildpacks")

        then:
        result.task(":verifyBuildpacks").outcome == TaskOutcome.SUCCESS
    }

    def "build task invokes pack with lazy DSL settings"() {
        given:
        File pack = writeFakePack()
        writeBasicProject()
        buildFile << """
            micronaut {
                buildpacks {
                    imageName.set("ghcr.io/acme/orders:1.0")
                    tags.add("ghcr.io/acme/orders:latest")
                    builder.set("example.com/builder:base")
                    runImage.set("example.com/run:base")
                    buildpacks.add("paketo-buildpacks/java")
                    environment.put("BP_JVM_VERSION", "25")
                    packExecutable.set("${pack.absolutePath}")
                    pullPolicy.set("if-not-present")
                    network.set("host")
                    dockerHost.set("tcp://docker.example:2375")
                    dockerConfigDirectory.set("${file('docker-config').absolutePath}")
                    trustBuilder.set(true)
                }
            }
        """

        when:
        def result = build("buildBuildpackImage")
        String args = file("pack-args.txt").text
        String env = file("pack-env.txt").text

        then:
        result.task(":buildBuildpackImage").outcome == TaskOutcome.SUCCESS
        args.contains("build ghcr.io/acme/orders:1.0")
        args.contains("--builder example.com/builder:base")
        args.contains("--run-image example.com/run:base")
        args.contains("--buildpack paketo-buildpacks/java")
        args.contains("--env BP_JVM_VERSION=25")
        args.contains("--tag ghcr.io/acme/orders:latest")
        args.contains("--pull-policy if-not-present")
        args.contains("--network host")
        args.contains("--trust-builder")
        !args.contains("--publish")
        env.contains("DOCKER_HOST=tcp://docker.example:2375")
        env.contains("DOCKER_CONFIG=${file('docker-config').absolutePath}")
    }

    def "publish and native tasks add publish and native image arguments"() {
        given:
        File pack = writeFakePack()
        writeBasicProject()
        buildFile << """
            micronaut {
                buildpacks {
                    imageName.set("ghcr.io/acme/orders:native")
                    environment.put("BP_NATIVE_IMAGE_BUILD_ARGUMENTS", "--verbose")
                    packExecutable.set("${pack.absolutePath}")
                }
            }
        """

        when:
        def result = build("publishNativeBuildpackImage")
        String args = file("pack-args.txt").text

        then:
        result.task(":publishNativeBuildpackImage").outcome == TaskOutcome.SUCCESS
        args.contains("--publish")
        args.contains("--env BP_NATIVE_IMAGE=true")
        args.contains("--env BP_NATIVE_IMAGE_BUILD_ARGUMENTS=--verbose")
    }

    def "native image default can be overridden through buildpack environment"() {
        given:
        File pack = writeFakePack()
        writeBasicProject()
        buildFile << """
            micronaut {
                buildpacks {
                    imageName.set("ghcr.io/acme/orders:native")
                    environment.put("BP_NATIVE_IMAGE", "false")
                    packExecutable.set("${pack.absolutePath}")
                }
            }
        """

        when:
        def result = build("buildNativeBuildpackImage")
        String args = file("pack-args.txt").text

        then:
        result.task(":buildNativeBuildpackImage").outcome == TaskOutcome.SUCCESS
        args.contains("--env BP_NATIVE_IMAGE=false")
        !args.contains("--env BP_NATIVE_IMAGE=true")
    }

    def "build task renders environment arguments deterministically"() {
        given:
        File pack = writeFakePack()
        writeBasicProject()
        buildFile << """
            micronaut {
                buildpacks {
                    environment.put("ZZZ_LAST", "last")
                    environment.put("AAA_FIRST", "first")
                    environment.put("MMM_MIDDLE", "middle")
                    packExecutable.set("${pack.absolutePath}")
                }
            }
        """

        when:
        def result = build("buildBuildpackImage")
        String args = file("pack-args.txt").text

        then:
        result.task(":buildBuildpackImage").outcome == TaskOutcome.SUCCESS
        args.indexOf("--env AAA_FIRST=first") < args.indexOf("--env MMM_MIDDLE=middle")
        args.indexOf("--env MMM_MIDDLE=middle") < args.indexOf("--env ZZZ_LAST=last")
    }

    def "missing pack executable reports actionable diagnostic"() {
        given:
        writeBasicProject()
        buildFile << """
            micronaut {
                buildpacks {
                    packExecutable.set("${file('missing-pack').absolutePath}")
                }
            }
        """

        when:
        def result = fails("buildBuildpackImage")

        then:
        result.output.contains("Unable to start the Cloud Native Buildpacks pack CLI")
        result.output.contains("micronaut.buildpacks.packExecutable")
    }

    def "non-zero pack build reports build failure diagnostic"() {
        given:
        File pack = writeFailingPack()
        writeBasicProject()
        buildFile << """
            micronaut {
                buildpacks {
                    packExecutable.set("${pack.absolutePath}")
                }
            }
        """

        when:
        def result = fails("buildBuildpackImage")

        then:
        result.output.contains("Cloud Native Buildpacks pack build failed")
        !result.output.contains("Unable to start the Cloud Native Buildpacks pack CLI")
    }

    private void writeBasicProject() {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.buildpacks"
            }

            micronaut {
                version "$micronautVersion"
            }

            $repositoriesBlock

            application { mainClass = "example.Application" }
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        testProjectDir.newFile("src/main/java/example/Application.java") << """
package example;

class Application {
    public static void main(String... args) {
    }
}
"""
        testProjectDir.newFolder("src", "main", "resources")
        testProjectDir.newFile("src/main/resources/application.yml") << "micronaut:\\n  application:\\n    name: hello-world\\n"
    }

    private File writeFakePack() {
        File pack = file("fake-pack")
        pack.text = """#!/bin/sh
printf '%s ' "\$@" > "${file('pack-args.txt').absolutePath}"
env | grep '^DOCKER_' > "${file('pack-env.txt').absolutePath}" || true
"""
        pack.setExecutable(true)
        pack
    }

    private File writeFailingPack() {
        File pack = file("failing-pack")
        pack.text = """#!/bin/sh
echo "pack failed" >&2
exit 7
"""
        pack.setExecutable(true)
        pack
    }
}
