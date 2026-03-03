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

    def "configurationValidationReport does not include duplicate logback resources on classpath"() {
        given:
        withSample("configuration-validation/basic-app")
        file("src/main/resources/logback.xml").text = """<configuration>
  <appender name=\"STDOUT\" class=\"ch.qos.logback.core.ConsoleAppender\">
    <encoder>
      <pattern>%msg%n</pattern>
    </encoder>
  </appender>
  <root level=\"INFO\">
    <appender-ref ref=\"STDOUT\"/>
  </root>
</configuration>
"""

        when:
        def result = build("configurationValidationReport")

        then:
        result.task(":configurationValidationReport").outcome == TaskOutcome.SUCCESS
        !result.output.contains("Resource [logback.xml] occurs multiple times on the classpath")
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
        file("src/main/resources/application.yml") << """
micronaut:
  jsonschema:
    configuration:
      validator:
        dependency-injection:
          enabled: true
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

    def "configurationValidation fails for dependency injection errors when opt-in is enabled"() {
        given:
        allowMavenLocal = true
        withSample("configuration-validation/basic-app")
        buildFile << """

            micronaut {
                configurationValidation {
                    version.set("2.0.0-M3")
                    validateDependencyInjection.set(true)
                    failOnNotPresent.set(false)
                    format.set("json")
                }
            }
        """
        file("src/main/resources/application.yml").text = """micronaut:
  application:
    name: basic-app
  server:
    port: 8080
  jsonschema:
    configuration:
      validator:
        dependency-injection:
          enabled: true
"""

        file("src/main/java/demo/app/MissingDependency.java").text = """package demo.app;

public final class MissingDependency {
}
"""
        file("src/main/java/demo/app/FieldInjectionBean.java").text = """package demo.app;

import io.micronaut.context.annotation.Context;
import jakarta.inject.Inject;

@Context
final class FieldInjectionBean {
    @Inject
    MissingDependency missingDependency;
}
"""

        when:
        def result = fails("configurationValidation")

        then:
        result.task(":configurationValidationReport").outcome == TaskOutcome.SUCCESS
        result.task(":configurationValidation").outcome == TaskOutcome.FAILED
        file("build/reports/micronaut/config-validation/production/configuration-errors.json").text.contains('"dependencyInjectionErrors"')
        file("build/reports/micronaut/config-validation/production/configuration-errors.json").text.contains('MissingDependency')
    }

    def "supports suppressedInjectionErrors patterns for dependency injection validation"() {
        given:
        allowMavenLocal = true
        withSample("configuration-validation/basic-app")
        buildFile << """

            micronaut {
                configurationValidation {
                    version.set("2.0.0-M3")
                    validateDependencyInjection.set(true)
                    suppressedInjectionErrors.add("demo.app.FieldInjectionBean")
                    failOnNotPresent.set(false)
                    format.set("json")
                }
            }
        """
        file("src/main/resources/application.yml").text = """micronaut:
  application:
    name: basic-app
  server:
    port: 8080
  jsonschema:
    configuration:
      validator:
        dependency-injection:
          enabled: true
"""

        file("src/main/java/demo/app/MissingDependency.java").text = """package demo.app;

public final class MissingDependency {
}
"""
        file("src/main/java/demo/app/FieldInjectionBean.java").text = """package demo.app;

import io.micronaut.context.annotation.Context;
import jakarta.inject.Inject;

@Context
final class FieldInjectionBean {
    @Inject
    MissingDependency missingDependency;
}
"""

        when:
        def result = fails("configurationValidation")

        then:
        result.output.contains("Unknown argument: --suppress-inject-errors")
    }

    def "dependency injection validation cache is invalidated when classpath changes"() {
        given:
        allowMavenLocal = true
        withSample("configuration-validation/basic-app")
        file("gradle.properties") << "org.gradle.caching=true\n"
        buildFile << """

            micronaut {
                configurationValidation {
                    version.set("2.0.0-M3")
                    validateDependencyInjection.set(true)
                    failOnNotPresent.set(false)
                }
            }
        """
        file("src/main/resources/application.yml").text = """micronaut:
  application:
    name: basic-app
  server:
    port: 8080
  jsonschema:
    configuration:
      validator:
        dependency-injection:
          enabled: true
"""
        when:
        def first = build("clean", "configurationValidationReport")

        then:
        first.task(":configurationValidationReport").outcome == TaskOutcome.SUCCESS

        when:
        def second = build("clean", "configurationValidationReport")

        then:
        second.task(":configurationValidationReport").outcome == TaskOutcome.FROM_CACHE

        when:
        file("src/main/java/demo/app/MissingDependency.java").text = """package demo.app;

public final class MissingDependency {
}
"""
        file("src/main/java/demo/app/FieldInjectionBean.java").text = """package demo.app;

import io.micronaut.context.annotation.Context;
import jakarta.inject.Inject;

@Context
final class FieldInjectionBean {
    @Inject
    MissingDependency missingDependency;
}
"""
        def third = fails("clean", "configurationValidation")

        then:
        third.task(":configurationValidationReport").outcome == TaskOutcome.SUCCESS
        third.task(":configurationValidation").outcome == TaskOutcome.FAILED
        file("build/reports/micronaut/config-validation/production/configuration-errors.json").text.contains('"dependencyInjectionErrors"')
        file("build/reports/micronaut/config-validation/production/configuration-errors.json").text.contains('MissingDependency')
    }
}
