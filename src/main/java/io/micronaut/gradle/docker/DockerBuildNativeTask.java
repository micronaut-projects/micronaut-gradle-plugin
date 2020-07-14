package io.micronaut.gradle.docker;

import io.micronaut.gradle.graalvm.NativeImageTask;
import org.gradle.api.tasks.TaskAction;

public class DockerBuildNativeTask extends NativeImageTask {

    @Override
    @TaskAction
    protected void exec() {
        super.exec();
    }
}
