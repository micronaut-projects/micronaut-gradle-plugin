package io.micronaut.gradle;

import com.diffplug.gradle.eclipse.apt.AptEclipsePlugin;
import io.micronaut.gradle.docker.MicronautDockerPlugin;
import io.micronaut.gradle.graalvm.MicronautGraalPlugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginManager;

/**
 * A plugin for a Micronaut application. Applies the "application" plugin.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautApplicationPlugin extends MicronautLibraryPlugin {

    @Override
    public void apply(Project project) {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(MicronautMinimalApplicationPlugin.class);
        pluginManager.apply(AptEclipsePlugin.class);
        pluginManager.apply(MicronautDockerPlugin.class);
        pluginManager.apply(MicronautGraalPlugin.class);
    }

}
