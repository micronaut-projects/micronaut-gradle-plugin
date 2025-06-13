package io.micronaut.gradle.openrewrite;

import org.gradle.api.provider.Property;

public interface MicronautOpenRewriteExtension {

    Property<Boolean> getAddDefaultRecipes();
    Property<String> getVersion();
}
