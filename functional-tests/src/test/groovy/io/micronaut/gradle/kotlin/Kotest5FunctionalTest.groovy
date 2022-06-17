package io.micronaut.gradle.kotlin

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

class Kotest5FunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    def "test kotest 5 test runtime with #plugin"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """plugins {
                       |    id("org.jetbrains.kotlin.jvm") version "1.6.21"
                       |    id("org.jetbrains.kotlin.kapt") version "1.6.21"
                       |    id("org.jetbrains.kotlin.plugin.allopen") version "1.6.21"
                       |    $plugin
                       |}
                       |
                       |tasks {
                       |    compileKotlin {
                       |        kotlinOptions {
                       |            jvmTarget = "1.8"
                       |        }
                       |    }
                       |    compileTestKotlin {
                       |        kotlinOptions {
                       |            jvmTarget = "1.8"
                       |        }
                       |    }
                       |}
                       |
                       |micronaut {
                       |    version "3.5.1"
                       |    runtime "netty"
                       |    testRuntime "kotest5"
                       |}
                       |
                       |dependencies {
                       |    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
                       |}
                       |
                       |$repositoriesBlock
                       |
                       |mainClassName="example.Application"
                       """.stripMargin()
        testProjectDir.newFolder("src", "test", "kotlin", "example")
        def testFile = testProjectDir.newFile("src/test/kotlin/example/ExampleTest.kt")
        testFile << """package com.example
                        |
                        |import io.micronaut.runtime.EmbeddedApplication
                        |import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
                        |import io.kotest.core.spec.style.StringSpec
                        |
                        |@MicronautTest
                        |class ExampleTest(private val application: EmbeddedApplication<*>): StringSpec({
                        |
                        |    "test the server is running" {
                        |        assert(application.isRunning)
                        |    }
                        |})
                        """.stripMargin()
        def configFile = testProjectDir.newFile("src/test/kotlin/example/ProjectConfig.kt")
        configFile << """package example
                      |
                      |import io.kotest.core.config.AbstractProjectConfig
                      |import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension
                      |
                      |object ProjectConfig : AbstractProjectConfig() {
                      |    override fun listeners() = listOf(MicronautKotest5Extension)
                      |    override fun extensions() = listOf(MicronautKotest5Extension)
                      |}
                      """.stripMargin()

        when:
        def result = build('test')

        def task = result.task(":test")
        println result.output

        then:
        task.outcome == TaskOutcome.SUCCESS

        where:
        plugin << [
                'id "io.micronaut.application"',
                'id "io.micronaut.minimal.application"',
        ]
    }
}
