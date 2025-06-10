package io.micronaut.gradle.openrewrite;

import io.micronaut.gradle.MicronautBasePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.openrewrite.gradle.RewritePlugin;

public class MicronautOpenRewritePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);
        project.getPluginManager().apply(RewritePlugin.class);
    }
}
