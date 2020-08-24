package io.micronaut.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.Collections;

/**
 * Settings for building Docker images.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DockerSettings {

    private final Property<String> from;
    private final Property<String> tag;
    private final ListProperty<Integer> ports;

    @Inject
    public DockerSettings(ObjectFactory objectFactory) {
        this.from = objectFactory.property(String.class);
        this.tag = objectFactory.property(String.class);
        this.ports = objectFactory.listProperty(Integer.class)
                                 .convention(Collections.singletonList(8080));
    }

    /**
     * @return The exposed port
     */
    public ListProperty<Integer> getPorts() {
        return ports;
    }

    /**
     * @return The tag to use when building an image
     */
    public Property<String> getTag() {
        return tag;
    }

    /**
     * @return The base image to use.
     */
    public Property<String> getFrom() {
        return from;
    }

    /**
     * Configures the base image.
     * @param from The base image
     * @return These settings
     */
    public DockerSettings from(String from) {
        this.from.set(from);
        return this;
    }

    /**
     * Configures the tag to use.
     * @param tag The tag
     * @return These settings
     */
    public DockerSettings tag(String tag) {
        this.tag.set(tag);
        return this;
    }

    /**
     * Configures the port to expose
     * @param port The port
     * @return These settings
     */
    public DockerSettings port(int port) {
        this.ports.add(port);
        return this;
    }
}
