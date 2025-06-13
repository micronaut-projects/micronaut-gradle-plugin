package io.micronaut.gradle.openrewrite;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public interface MicronautOpenRewriteExtension {

    @Input
    @Optional
    Property<String> getVersion();
}
