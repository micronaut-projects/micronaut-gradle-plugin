package io.micronaut.gradle.fixtures

import org.gradle.testkit.runner.BuildResult

class AbstractEagerConfiguringFunctionalTest extends AbstractFunctionalTest {
    @Override
    BuildResult build(String... args) {
        return super.build("tasks", *args)
    }
}
