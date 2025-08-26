package io.micronaut.gradle.openrewrite;

import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.MicronautExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.openrewrite.gradle.RewritePlugin;

import java.util.List;

public class MicronautOpenRewritePlugin implements Plugin<Project> {
    // TODO: Use real Micronaut Projectgen Recipes coordinates when release is out
    public static String RECIPE_GA_COORDINATES =
        System.getProperty("mn.openrewrite.recipes.coords", "org.openrewrite:rewrite-java");
    private static final String EXTENSION = "openrewrite";
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

        openRewriteExtension.getAddDefaultRecipes().convention(true);
        openRewriteExtension.getVersion().convention(VERSION);

        project.getPlugins().withType(RewritePlugin.class, plugin -> {
            Configuration rewriteConfig = project.getConfigurations().getByName("rewrite");
            DependencyHandler dependencies = project.getDependencies();

            rewriteConfig.getDependencies().addAllLater(
                    openRewriteExtension.getAddDefaultRecipes().zip(
                            openRewriteExtension.getVersion(),
                            (enabled, version) -> {
                                if (Boolean.TRUE.equals(enabled)) {
                                    String coordinate = RECIPE_GA_COORDINATES + ":" + version;
                                    return List.of(dependencies.create(coordinate));
                                }
                                return List.of();
                            }
                    )
            );
        });
    }
}
