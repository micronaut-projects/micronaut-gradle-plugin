package io.micronaut.gradle.configuration.validation

import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

class ConfigurationValidationFunctionalTest extends AbstractFunctionalTest {

    @Override
    protected void withSample(String name) {
        super.withSample(name)
        // Samples include their own settings.gradle, so we must patch it after copying.
        patchSettings()
    }

    def "configurationValidation succeeds for valid production config"() {
        given:
        withSample("configuration-validation/basic-app")

        when:
        def result = build("configurationValidation")

        then:
        result.task(":configurationValidationReport").outcome == TaskOutcome.SUCCESS
        result.task(":configurationValidation").outcome == TaskOutcome.SUCCESS
        file("build/reports/micronaut/config-validation/production/result.properties").exists()
    }

    def "runConfigurationValidation fails for invalid configuration"() {
        given:
        withSample("configuration-validation/basic-app")
        file("src/main/resources/application.yml").text = """micronaut:\n  application:\n    name: basic-app\n  server:\n    port: notAnInt\n"""

        when:
        def result = fails("runConfigurationValidation")

        then:
        result.output.contains("Micronaut configuration validation failed")
        result.task(":runConfigurationValidationReport").outcome == TaskOutcome.SUCCESS
        result.task(":runConfigurationValidation").outcome == TaskOutcome.FAILED
        file("build/reports/micronaut/config-validation/run/result.properties").exists()
    }

    def "report task is cacheable and cached failures re-fail"() {
        given:
        withSample("configuration-validation/basic-app")
        file("gradle.properties") << "org.gradle.caching=true\n"
        file("src/main/resources/application.yml").text = """micronaut:\n  application:\n    name: basic-app\n  server:\n    port: notAnInt\n"""

        when:
        def result = build("configurationValidationReport")

        then:
        result.task(":configurationValidationReport").outcome == TaskOutcome.SUCCESS

        when:
        def cached = build("clean", "configurationValidationReport")

        then:
        cached.task(":configurationValidationReport").outcome == TaskOutcome.FROM_CACHE

        when:
        def failAgain = fails("clean", "configurationValidation")

        then:
        failAgain.task(":configurationValidationReport").outcome == TaskOutcome.FROM_CACHE
        failAgain.task(":configurationValidation").outcome == TaskOutcome.FAILED
    }

    def "testConfigurationValidation fails when test environment config is invalid"() {
        given:
        withSample("configuration-validation/basic-app")
        testProjectDir.newFolder("src", "test", "resources")
        file("src/test/resources/application-test.yml").text = """micronaut:\n  server:\n    port: notAnInt\n"""

        when:
        def result = fails("testConfigurationValidation")

        then:
        result.task(":testConfigurationValidationReport").outcome == TaskOutcome.SUCCESS
        result.task(":testConfigurationValidation").outcome == TaskOutcome.FAILED
    }

    def "supports customization of environments and report outputs"() {
        given:
        withSample("configuration-validation/basic-app")
        // This file should only be loaded when the 'dev' environment is active.
        file("src/main/resources/application-dev.yml").text = """micronaut:\n  server:\n    port: notAnInt\n"""

        and: "default production validation doesn't enable dev"
        def baseline = build("configurationValidation")
        assert baseline.task(":configurationValidation").outcome == TaskOutcome.SUCCESS

        and: "enable dev + customize outputs"
        buildFile << """

            micronaut {
                configurationValidation {
                    assemble {
                        environments.set(["dev"])
                        format.set("json")
                        outputDirectory.set(layout.buildDirectory.dir("custom-config-validation"))
                    }
                }
            }
        """

        when:
        def result = fails("configurationValidation")

        then:
        result.task(":configurationValidation").outcome == TaskOutcome.FAILED
        file("build/custom-config-validation/configuration-errors.json").exists()
        !file("build/custom-config-validation/configuration-errors.html").exists()
    }

    def "gradle test runs testConfigurationValidation"() {
        given:
        withSample("configuration-validation/basic-app")
        testProjectDir.newFolder("src", "test", "resources")
        file("src/test/resources/application-test.yml").text = """micronaut:\n  server:\n    port: notAnInt\n"""

        when:
        def result = fails("test")

        then:
        result.task(":testConfigurationValidationReport").outcome == TaskOutcome.SUCCESS
        result.task(":testConfigurationValidation").outcome == TaskOutcome.FAILED
    }

    def "gradle assemble runs configurationValidation"() {
        given:
        withSample("configuration-validation/basic-app")
        file("src/main/resources/application.yml").text = """micronaut:\n  application:\n    name: basic-app\n  server:\n    port: notAnInt\n"""

        when:
        def result = fails("assemble")

        then:
        result.task(":configurationValidationReport").outcome == TaskOutcome.SUCCESS
        result.task(":configurationValidation").outcome == TaskOutcome.FAILED
    }

    def "validates Micronaut-generated configuration properties schema (ServerSslConfiguration)"() {
        given:
        withSample("configuration-validation/basic-app")
        file("src/main/resources/application.yml").text = """micronaut:\n  server:\n    ssl:\n      enabled: notABoolean\n"""

        when:
        def result = fails("configurationValidation")

        then:
        result.output.contains("micronaut.server.ssl.enabled")
        result.task(":configurationValidationReport").outcome == TaskOutcome.SUCCESS
        result.task(":configurationValidation").outcome == TaskOutcome.FAILED
    }
}
