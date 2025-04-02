package io.micronaut.gradle.jsonschema;

import io.micronaut.core.annotation.Internal;
import io.micronaut.sourcegen.annotations.GenerateGradlePlugin;
import io.micronaut.sourcegen.annotations.GenerateGradlePlugin.GenerateGradleTask;

@GenerateGradlePlugin(
    namePrefix = "AbstractJSONSchema",
    taskGroup = "jsonschema",
    tasks = @GenerateGradleTask(
        source = "io.micronaut.jsonschema.generator.plugin.JsonSchemaGeneratorTask",
        namePrefix = "GenerateJSONSchema",
        extensionMethodName = "generate"
    )
)
@Internal
interface PluginGenerationTrigger {
}
