package io.micronaut.gradle.crac;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
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
     * The platform to specify in the FROM instruction defaults to {@value MicronautCRaCPlugin#CRAC_DEFAULT_BASE_IMAGE_PLATFORM}.
     * @return the platform (can be removed with {@code platform.convention(null)} in the {@link CRaCConfiguration} extension)
     */
    Property<String> getPlatform();

    /**
     * Any arguments passed to java in the final image
     * @return list of arguments
     */
    ListProperty<String> getFinalArgs();

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
