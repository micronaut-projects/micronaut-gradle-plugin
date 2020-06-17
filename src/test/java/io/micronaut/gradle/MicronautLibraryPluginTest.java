package io.micronaut.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MicronautLibraryPluginTest {

    @Test
    void testMicronautLibraryPlugin() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("io.micronaut.micronaut-library");

        MicronautExtension ext = project
                .getExtensions().getByType(MicronautExtension.class);
        ext.processing(builder -> {
            builder.group("test.group");
            builder.module("test.module");
        });
        MicronautExtension.AnnotationProcessingConfig processing = ext.getProcessingConfig();

        assertNotNull(processing);
        assertEquals("test.group", processing.getGroup().get());
    }
}
