package io.micronaut.gradle.lambda

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable })
@IgnoreIf({ os.windows })
class LambdaNativeImageSpec extends AbstractFunctionalTest {

    void 'mainclass is set correctly for an application deployed as GraalVM and Lambda'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }
            
            $repositoriesBlock
            
            application {
                mainClass.set("com.example.Application")
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.endsWith(' io.micronaut.function.aws.runtime.MicronautLambdaRuntime') }
        !dockerFileNative.find() { it.endsWith('com.example.Application') }
    }

    void 'native lambdas build in docker fetch the correct graalvm for #desc'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }

            dockerfileNative {
                graalArch.set('$archset')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find { it ==~ /.*graalvm-jdk-\d+_linux-${archset}_bin\.tar\.gz.*/ }

        where:
        archset   | desc
        'aarch64' | 'ARM architecture'
        'x64'     | 'Intel architecture'
    }

    void 'it is possible to define the mainclass for a dockerfile native'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }
            
            $repositoriesBlock
            
            application {
                mainClass.set("com.example.Application")
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }
            
            graalvmNative {
                binaries {
                    main {
                        mainClass.set("my.own.main.class")
                    }
                }
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains('my.own.main.class') }
        !dockerFileNative.find() { it.contains('com.example.Application') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/171")
    void 'mainclass is set correctly for an AWS Lambda function using custom-runtime and GraalVM'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            dependencies {
                implementation("io.micronaut.validation:micronaut-validation")
                implementation("io.micronaut:micronaut-runtime")
                implementation("io.micronaut.aws:micronaut-function-aws")
                implementation("io.micronaut.aws:micronaut-function-aws-custom-runtime")
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }

            application {
                mainClass.set("com.example.BookLambdaRuntime")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        !dockerFileNative.find() { it.endsWith(' io.micronaut.function.aws.runtime.MicronautLambdaRuntime') }
        dockerFileNative.find() { it.endsWith('com.example.BookLambdaRuntime') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/279")
    void 'baseImage is overridden correctly for an AWS Lambda function using custom-runtime and GraalVM'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            dependencies {
                implementation("io.micronaut.validation:micronaut-validation")
                implementation("io.micronaut:micronaut-runtime")
                implementation("io.micronaut.aws:micronaut-function-aws")
                implementation("io.micronaut.aws:micronaut-function-aws-custom-runtime")
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }

            application {
                mainClass.set("com.example.BookLambdaRuntime")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }

            dockerfileNative {
                baseImage('internal.proxy.com/amazonlinux:2023')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains('internal.proxy.com/amazonlinux:2023') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/279")
    void 'baseImage is set correctly for an AWS Lambda function using custom-runtime and GraalVM'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            dependencies {
                implementation("io.micronaut.validation:micronaut-validation")
                implementation("io.micronaut:micronaut-runtime")
                implementation("io.micronaut.aws:micronaut-function-aws")
                implementation("io.micronaut.aws:micronaut-function-aws-custom-runtime")
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }

            application {
                mainClass.set("com.example.BookLambdaRuntime")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains('amazonlinux:2023') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/pull/537")
    void 'it is possible to define the GraalVM releases URL for a dockerfile native'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }
            
            $repositoriesBlock
            
            application {
                mainClass.set("com.example.Application")
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }
            
            graalvmNative {
                binaries {
                    main {
                        mainClass.set("my.own.main.class")
                    }
                }
            }

            tasks.named("dockerfileNative") {
                graalReleasesUrl = "https://releases.company.com/downloads"
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        !dockerFileNative.find() { it.contains('https://github.com/graalvm/graalvm-ce-builds/releases/download') }
        dockerFileNative.find() { it.contains('https://releases.company.com/downloads') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/753")
    void 'can pickup a different native lambda runtime'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """import io.micronaut.gradle.graalvm.NativeLambdaRuntime
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            dependencies {
                implementation("io.micronaut.validation:micronaut-validation")
                implementation("io.micronaut:micronaut-runtime")
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }

            micronaut {
                nativeLambda {
                    lambdaRuntime = NativeLambdaRuntime.$runtime
                }
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.endsWith(mainClass) }

        where:
        runtime          | mainClass
        'API_GATEWAY_V1' | 'io.micronaut.function.aws.runtime.MicronautLambdaRuntime'
        'API_GATEWAY_V2' | 'io.micronaut.function.aws.runtime.APIGatewayV2HTTPEventMicronautLambdaRuntime'
        'ALB'            | 'io.micronaut.function.aws.runtime.ApplicationLoadBalancerMicronautLambdaRuntime'
    }

    void 'docker buildx native task uses target architecture mapping for lambda images'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """import io.micronaut.gradle.docker.DockerBuildx
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }

            version = "0.1"

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('25')
                targetCompatibility = JavaVersion.toVersion('25')
            }

            tasks.named("dockerBuildxNative", DockerBuildx) {
                dockerExecutable = file("fake-docker.sh").absolutePath
                platforms = ["linux/amd64", "linux/arm64"]
                images = ["example.com/demo/native:0.1"]
                builder = "multiarch-builder"
            }
        """
        testProjectDir.newFolder("src", "main", "java", "com", "example")
        def javaFile = testProjectDir.newFile("src/main/java/com/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package com.example;

class Application {
    public static void main(String... args) {
    }
}
"""
        def fakeDocker = file("fake-docker.sh")
        fakeDocker.text = """#!/bin/sh
set -eu
mkdir -p "\$PWD/build"
printf '%s\\n' "\$@" > "\$PWD/build/buildx-native-args.txt"
"""
        fakeDocker.setExecutable(true)

        when:
        def result = build('dockerBuildxNative')

        then:
        result.task(':dockerBuildxNative').outcome == TaskOutcome.SUCCESS

        and:
        def dockerfileBuildxNative = file('build/docker/native-main/DockerfileBuildxNative').text
        dockerfileBuildxNative.contains('ARG TARGETARCH')
        dockerfileBuildxNative.contains('amd64) printf x64')
        dockerfileBuildxNative.contains('arm64) printf aarch64')
        dockerfileBuildxNative.contains('graalvm-jdk-25_linux-$(case "${TARGETARCH}" in amd64) printf x64 ;; arm64) printf aarch64 ;; *) printf \'%s\' "${TARGETARCH}" ;; esac)_bin.tar.gz')

        and:
        file("build/buildx-native-args.txt").readLines() == [
                "buildx",
                "build",
                "--builder",
                "multiarch-builder",
                "--platform",
                "linux/amd64,linux/arm64",
                "--tag",
                "example.com/demo/native:0.1",
                "--push",
                "--file",
                file("build/docker/native-main/DockerfileBuildxNative").absolutePath,
                file("build/docker/native-main").absolutePath
        ]
    }
}
