package io.micronaut.gradle.crac;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;
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
     * The optional docker network name to use during building
     * @return the network name
     */
    @Optional
    Property<String> getNetwork();

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

    /**
     * Prior to creating a checkpoint and running the warmup script, we require to know if the application is up.
     * This property allows you to define your own check for this if the default {@value MicronautCRaCPlugin#CRAC_DEFAULT_READINESS_COMMAND} is not sufficient.
     *
     * @return the bash command to run to check the app is running, it should exit with 0 if the app is up.
     */
    Property<String> getPreCheckpointReadinessCommand();
}
