package io.micronaut.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public class DockerSettings {

    private final Property<String> from;
    private final Property<String> tag;
    private final Property<Integer> port;

    @Inject
    public DockerSettings(ObjectFactory objectFactory) {
        this.from = objectFactory.property(String.class);
        this.tag = objectFactory.property(String.class);
        this.port = objectFactory.property(Integer.class).convention(8080);
    }

    public Property<Integer> getPort() {
        return port;
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

    public DockerSettings port(int port) {
        this.port.set(port);
        return this;
    }
}
