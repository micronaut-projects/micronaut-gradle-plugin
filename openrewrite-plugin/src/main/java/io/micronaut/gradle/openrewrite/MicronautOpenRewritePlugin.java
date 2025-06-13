package io.micronaut.gradle.openrewrite;

import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.MicronautExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.openrewrite.gradle.RewritePlugin;

import java.util.Collections;

public class MicronautOpenRewritePlugin implements Plugin<Project> {
    private static final String EXTENSION = "rewrite";
    private static final String VERSION = "+";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);
        project.getPluginManager().apply(RewritePlugin.class);

        MicronautExtension micronautExtension = project
                .getExtensions()
                .getByType(MicronautExtension.class);

        MicronautOpenRewriteExtension openRewriteExtension = micronautExtension.getExtensions()
                .create(EXTENSION, MicronautOpenRewriteExtension.class);

        openRewriteExtension.getVersion().convention(VERSION);

        project.getPlugins().withType(RewritePlugin.class, plugin -> {
            Configuration rewriteConfig = project.getConfigurations().getByName("rewrite");
            DependencyHandler dependencies = project.getDependencies();

            rewriteConfig.getDependencies().addAllLater(openRewriteExtension.getVersion().map(version -> {
                if (version != null && !version.trim().isEmpty()) {
                    String coordinate = "io.micronaut.micronaut-projectgen:projectgen-recipes:" + version;
                    return Collections.singleton(dependencies.create(coordinate));
                }
                return Collections.emptyList();
            }));
        });
    }
}