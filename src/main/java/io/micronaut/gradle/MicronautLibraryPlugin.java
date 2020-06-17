package io.micronaut.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;

public class MicronautLibraryPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(getBasePluginName());
        ExtensionContainer extensions = project.getExtensions();
        extensions.create("micronaut", MicronautExtension.class);
    }

    protected String getBasePluginName() {
        return "java-library";
    }
}
