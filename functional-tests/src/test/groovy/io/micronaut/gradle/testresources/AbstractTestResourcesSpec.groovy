package io.micronaut.gradle.testresources

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest

abstract class AbstractTestResourcesSpec extends AbstractEagerConfiguringFunctionalTest {

    @Override
    protected void withSample(String name) {
        super.withSample(name)
        patchSettings()
    }

}
