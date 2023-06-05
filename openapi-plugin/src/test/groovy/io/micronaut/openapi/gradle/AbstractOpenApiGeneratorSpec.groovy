package io.micronaut.openapi.gradle

import io.micronaut.gradle.AbstractGradleBuildSpec

class AbstractOpenApiGeneratorSpec extends AbstractGradleBuildSpec {
    protected void withPetstore() {
        file("petstore.json").text = this.class.getResourceAsStream("/petstore.json").getText("UTF-8")
    }
}
