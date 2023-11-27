package io.micronaut.gradle.openapi

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import spock.lang.Shared

class AbstractOpenApiWithKotlinSpec extends AbstractEagerConfiguringFunctionalTest {
    @Shared
    protected final String kotlinVersion = System.getProperty("kotlinVersion")

    @Shared
    protected final String kspVersion = System.getProperty("kspVersion")

    protected void withPetstore() {
        file("petstore.json").text = this.class.getResourceAsStream("/petstore.json").getText("UTF-8")
    }
}
