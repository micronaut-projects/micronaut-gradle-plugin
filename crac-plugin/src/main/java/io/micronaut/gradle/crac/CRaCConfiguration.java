package io.micronaut.gradle.crac;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

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
    RegularFileProperty getWarmupScript();

    /**
     * The checkpoint script to copy in to the checkpoint image and execute prior to snapshotting.
     * @return The script to add to the checkpoint image.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    RegularFileProperty getCheckpointScript();
}
