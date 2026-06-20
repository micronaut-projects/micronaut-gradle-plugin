package io.micronaut.gradle.testresources

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest

abstract class AbstractTestResourcesSpec extends AbstractEagerConfiguringFunctionalTest {
    private static final String TEST_RESOURCES_MICRONAUT_VERSION = "4.10.10"

    @Override
    protected void withSample(String name) {
        super.withSample(name)
        overrideMicronautVersion(TEST_RESOURCES_MICRONAUT_VERSION)
        patchSettings()
    }

}
