package io.micronaut.gradle.openapi

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Shared

class AbstractOpenApiWithKotlinSpec extends AbstractEagerConfiguringFunctionalTest {
    @Shared
    protected final String kotlinVersion = System.getProperty("kotlinVersion")

    @Shared
    protected final String kotlin2Version = System.getProperty("kotlin2Version")

    @Shared
    protected final String kspVersion = System.getProperty("kspVersion")

    @Shared
    protected final String ksp2Version = System.getProperty("ksp2Version")

    @Override
    protected boolean usesDebugRunner() {
        false
    }

    protected void withPetstore() {
        file("petstore.json").text = this.class.getResourceAsStream("/petstore.json").getText("UTF-8")
    }

    @Override
    GradleRunner configureRunner(String... args) {
        super.configureRunner(args).withDebug(false)
    }
}
