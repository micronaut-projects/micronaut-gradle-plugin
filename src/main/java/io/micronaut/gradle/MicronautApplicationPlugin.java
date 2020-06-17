package io.micronaut.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.JavaExec;

import java.util.LinkedHashMap;
import java.util.Map;

public class MicronautApplicationPlugin extends MicronautLibraryPlugin {
    @Override
    public void apply(Project project) {
        super.apply(project);

        Configuration developmentOnly = project
                .getConfigurations().create("developmentOnly");


        project.getTasks().withType(JavaExec.class, javaExec -> {
            javaExec.getClasspath().plus(developmentOnly.getAsFileTree());
            if (project.getGradle().getStartParameter().isContinuous()) {
                Map<String, Object> sysProps = new LinkedHashMap<>();
                sysProps.put("micronaut.io.watch.restart", "true");
                sysProps.put("micronaut.io.watch.enabled", "true");
                javaExec.systemProperties(
                        sysProps
                );
            }
        });
    }

    @Override
    protected String getBasePluginName() {
        return "application";
    }
}
