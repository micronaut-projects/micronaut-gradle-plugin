package io.micronaut.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public class DockerSettings {

    private final Property<String> from;
    private final Property<String> tag;

    @Inject
    public DockerSettings(ObjectFactory objectFactory) {
        this.from = objectFactory.property(String.class);
        this.tag = objectFactory.property(String.class);
    }

    public Property<String> getTag() {
        return tag;
    }

    public Property<String> getFrom() {
        return from;
    }

    public DockerSettings from(String from) {
        this.from.set(from);
        return this;
    }

    public DockerSettings tag(String tag) {
        this.tag.set(tag);
        return this;
    }
}
