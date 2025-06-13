package io.micronaut.gradle.openrewrite;

import org.gradle.api.provider.Property;

public interface MicronautOpenRewriteExtension {

    Property<String> getVersion();
}
