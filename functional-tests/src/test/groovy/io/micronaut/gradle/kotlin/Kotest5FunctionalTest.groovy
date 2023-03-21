package io.micronaut.gradle.kotlin

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared

class Kotest5FunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    @Shared
    private final String kotlinVersion = System.getProperty("kotlinVersion")
    @Shared
    private final String kspVersion = System.getProperty("kspVersion")

    @Shared
    private final String kaptPlugin = "id(\"org.jetbrains.kotlin.kapt\") version \"$kotlinVersion\""
    @Shared
    private final String kspPlugin = "id(\"com.google.devtools.ksp\") version \"$kspVersion\""

    def "test kotest 5 test runtime with #plugin and #processingPlugin"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """plugins {
                       |    id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
                       |    $processingPlugin
                       |    id("org.jetbrains.kotlin.plugin.allopen") version "$kotlinVersion"
                       |    $plugin
                       |}
                       |
                       |micronaut {
                       |    version "$micronautVersion"
                       |    runtime "netty"
                       |    testRuntime "kotest5"
                       |}
                       |
                       |dependencies {
                       |    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
                       |    runtimeOnly("io.micronaut.serde:micronaut-serde-jackson")
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
        def result = build('test', '-s')

        def task = result.task(":test")
        println result.output

        then:
        task.outcome == TaskOutcome.SUCCESS

        where:
        plugin                                  | processingPlugin
        'id "io.micronaut.application"'         | kaptPlugin
        'id "io.micronaut.minimal.application"' | kaptPlugin
        'id "io.micronaut.application"'         | kspPlugin
        'id "io.micronaut.minimal.application"' | kspPlugin

    }
}
