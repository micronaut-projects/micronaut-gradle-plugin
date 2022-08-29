package io.micronaut.gradle.crac;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.nio.file.Path;

public interface CRaCConfiguration {

    /**
     * If set to false, CRaC support will be disabled.
     * @return the enabled property
     */
    Property<Boolean> getEnabled();

    /**
     * The base image to use for CRaC checkpoint generation and the final image.
     * @return the base image
     */
    Property<String> getBaseImage();

    /**
     * The resultant image name
     * @return the final image name
     */
    Property<String> getImageName();

    /**
     * The warmup script to copy in to the checkpoint image and execute prior to snapshotting.
     * @return The script to add to the checkpoint image.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    Property<Path> getWarmupScript();

    /**
     * The checkpoint script to copy in to the checkpoint image and execute prior to snapshotting.
     * @return The script to add to the checkpoint image.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    Property<Path> getCheckpointScript();
}
