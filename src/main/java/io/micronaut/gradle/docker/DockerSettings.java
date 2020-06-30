package io.micronaut.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public class DockerSettings {

    private final Property<String> from;

    @Inject
    public DockerSettings(ObjectFactory objectFactory) {
        this.from = objectFactory.property(String.class);
    }

    public Property<String> getFrom() {
        return from;
    }

    public DockerSettings from(String from) {
        this.from.set(from);
        return this;
    }
}
