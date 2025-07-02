package io.micronaut.gradle

import org.gradle.api.artifacts.Configuration
import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.regex.Pattern

class GenerateImportFactoryTaskSpec extends Specification {

    @TempDir
    Path tempDir

    Project project
    GenerateImportFactoryTask task
    Configuration runtimeClasspath

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply('java')

        task = project.tasks.register('testGenerate', GenerateImportFactoryTask) { t ->
            t.getRuntimeClasspath().from(project.configurations.runtimeClasspath)
            t.getGeneratedSourcesDir().set(project.layout.buildDirectory.dir("gen-src/import"))
            t.getIncludeDependenciesFilter().set('^.*:.*$')
            t.getExcludeDependenciesFilter().set('^$')
            t.getIncludePackagesFilter().set('^.*$')
            t.getExcludePackagesFilter().set('^$')
        }.get()
    }

    def cleanup() {
        def genDir = task.getGeneratedSourcesDir().get().asFile
        if (genDir.exists()) {
            genDir.deleteDir()
        }
    }

    def "task has default values configured"() {
        expect:
        task.getIncludeDependenciesFilter().get() == '^.*:.*$'
        task.getExcludeDependenciesFilter().get() == '^$'
        task.getIncludePackagesFilter().get() == '^.*$'
        task.getExcludePackagesFilter().get() == '^$'
        task.getGeneratedSourcesDir().get().asFile.path.contains('gen-src/import')
    }

    def "task generates nothing when no matching dependencies found"() {
        given:
        task.getIncludeDependenciesFilter().set('^nonexistent:nonexistent$')

        when:
        task.generate()

        then:
        def outputDir = task.getGeneratedSourcesDir().get().asFile
        !outputDir.exists() || (outputDir.exists() && outputDir.listFiles().length == 0)
    }

    def "task generates nothing when no matching packages found"() {
        given:
        task.getIncludePackagesFilter().set('^nonexistent$')

        // Create test JAR and add to implementation configuration
        def jarFile = createTestJar('test.jar', ['com/example/Test.class'])
        project.dependencies.add('implementation', project.files(jarFile))

        when:
        task.generate()

        then:
        def outputDir = task.getGeneratedSourcesDir().get().asFile
        !outputDir.exists() || (outputDir.exists() && outputDir.listFiles().length == 0)
    }

    def "task generates factory files for matching packages"() {
        given: "A properly structured test JAR"
        def jarFile = createTestJar('test.jar', [
                'com/example/Test.class',
                'org/acme/Foo.class'
        ])

        and: "JAR added to implementation configuration"
        project.dependencies.add('implementation', project.files(jarFile))

        and: "Task configured to include all (override defaults for this test)"
        task.getIncludePackagesFilter().set('.*')  // Match any package
        task.getExcludePackagesFilter().set('^$')  // No packages excluded
        task.getIncludeDependenciesFilter().set('.*')  // Match any dependency
        task.getExcludeDependenciesFilter().set('^$')  // No dependencies excluded

        when: "Task executes"
        task.generate()

        then: "Verify output directory exists and has content"
        def outputDir = task.generatedSourcesDir.get().asFile
        outputDir.exists()

        and: "Check that factory files were generated for expected packages"
        def factoryFiles = []
        outputDir.eachFileRecurse { file ->
            if (file.isFile() && file.name == 'ImportFactory.java') {
                factoryFiles << file
            }
        }
        // Verify specific factory files and their content
        def exampleFactory = outputDir.toPath().resolve('com/example/ImportFactory.java')
        def acmeFactory = outputDir.toPath().resolve('org/acme/ImportFactory.java')

        Files.exists(exampleFactory)
        Files.exists(acmeFactory)

        // Verify content of com.example.ImportFactory
        def exampleContent = Files.readString(exampleFactory)
        exampleContent.contains('package com.example;')
        exampleContent.contains('@Import(packages = {\n        "com.example"\n})')

        // Verify content of org.acme.ImportFactory
        def acmeContent = Files.readString(acmeFactory)
        acmeContent.contains('package org.acme;')
        acmeContent.contains('@Import(packages = {\n        "org.acme"\n})')
    }

    def "task generates single factory file when target package is specified"() {
        given:
        // Make sure task.getTargetPackage() is defined in GenerateImportFactoryTask as a Property<String>
        // e.g., public abstract Property<String> getTargetPackage();
        task.getTargetPackage().set('io.micronaut.generated')
        task.getIncludePackagesFilter().set('.*') // Ensure all packages are considered
        task.getExcludePackagesFilter().set('^$')
        task.getIncludeDependenciesFilter().set('.*')
        task.getExcludeDependenciesFilter().set('^$')

        def jarFile = createTestJar('test.jar', [
                'com/example/Test.class',
                'org/acme/Foo.class'
        ])
        project.dependencies.add('implementation', project.files(jarFile))

        when:
        task.generate()

        then:
        def outputDir = task.getGeneratedSourcesDir().get().asFile
        def targetFile = outputDir.toPath().resolve('io/micronaut/generated/ImportFactory.java')

        Files.exists(targetFile) // This expects a Path, which targetFile now is.

        and:
        def content = Files.readString(targetFile)
        content.contains('package io.micronaut.generated;')
        content.contains('@Factory')
        content.contains('@Import(packages = {')
        content.contains('"com.example"')
        content.contains('"org.acme"')
        def factoryFiles = []
        outputDir.eachFileRecurse { file ->
            if (file.isFile() && file.name == 'ImportFactory.java') {
                factoryFiles << file
            }
        }
        factoryFiles.size() == 1
    }

    def "task respects include/exclude dependency filters"() {
        given:
        // Set filters using names that match the JAR files directly
        task.getIncludeDependenciesFilter().set('^included-.*\\.jar$')
        task.getExcludeDependenciesFilter().set('^included-but-excluded\\.jar$')
        task.getIncludePackagesFilter().set('.*') // Include all packages for this test
        task.getExcludePackagesFilter().set('^$')

        def includedJar = createTestJar('included-1.jar', ['com/example/Included.class'])
        def excludedJar = createTestJar('included-but-excluded.jar', ['com/example/Excluded.class'])
        def normalJar = createTestJar('regular.jar', ['com/example/Regular.class'])

        project.dependencies.add('implementation', project.files(includedJar))
        project.dependencies.add('implementation', project.files(excludedJar))
        project.dependencies.add('implementation', project.files(normalJar))

        when:
        task.generate()

        then:
        def outputDir = task.getGeneratedSourcesDir().get().asFile
        def exampleFactory = outputDir.toPath().resolve('com/example/ImportFactory.java')
        Files.exists(exampleFactory)
        def exampleContent = Files.readString(exampleFactory)
        exampleContent.contains('package com.example;')
        exampleContent.contains('@Import(packages = {\n        "com.example"\n})')
        // No factory should be created in subdirectories like 'com/example/excluded' or 'com/example/regular'
        def excludedFactoryPath = outputDir.toPath().resolve('com/example/excluded/ImportFactory.java')
        !Files.exists(excludedFactoryPath)

        def normalFactoryPath = outputDir.toPath().resolve('com/example/regular/ImportFactory.java')
        !Files.exists(normalFactoryPath)

        // Refined checks for the generated factory content:
        def generatedFactoryFiles = []
        outputDir.eachFileRecurse { file ->
            if (file.isFile() && file.name == 'ImportFactory.java') {
                generatedFactoryFiles << file
            }
        }
        generatedFactoryFiles.size() == 1
        generatedFactoryFiles[0].name == 'ImportFactory.java' // Ensure it's the correct name
        generatedFactoryFiles[0].toPath().parent.fileName.toString() == 'example' // Ensure it's in com/example

        def finalFactoryContent = Files.readString(generatedFactoryFiles[0].toPath())
        finalFactoryContent.contains('package com.example;')
        finalFactoryContent.contains('@Import(packages = {')
        finalFactoryContent.contains('"com.example"')
        finalFactoryContent.contains('})')

        !Files.exists(excludedFactoryPath)
        !Files.exists(normalFactoryPath)
    }

    def "task respects include/exclude package filters"() {
        given:
        task.getIncludePackagesFilter().set('^com\\.included\\..*$')  // Match only 'com.included.*'
        task.getExcludePackagesFilter().set('^com\\.included\\.excluded$') // Exclude 'com.included.excluded.*'
        task.getIncludeDependenciesFilter().set('.*') // Include all dependencies for this test
        task.getExcludeDependenciesFilter().set('^$')

        def jarFile = createTestJar('test.jar', [
                'com/included/foo/TestFoo.class',
                'com/included/bar/TestBar.class',
                'com/included/excluded/TestExcluded.class',
                'com/other/TestOther.class'
        ])
        project.dependencies.add('implementation', project.files(jarFile))

        when:
        task.generate()

        then:
        def outputDir = task.getGeneratedSourcesDir().get().asFile
        def fooFactory = outputDir.toPath().resolve('com/included/foo/ImportFactory.java')
        Files.exists(fooFactory)
        def fooContent = Files.readString(fooFactory)
        fooContent.contains('package com.included.foo;')
        fooContent.contains('"com.included.foo"')

        // Expect factory for 'com.included.bar'
        def barFactory = outputDir.toPath().resolve('com/included/bar/ImportFactory.java')
        Files.exists(barFactory)
        def barContent = Files.readString(barFactory)
        barContent.contains('package com.included.bar;')
        barContent.contains('"com.included.bar"')

        // Expect NO factory for 'com.included.excluded'
        def excludedFactory = outputDir.toPath().resolve('com/included/excluded/ImportFactory.java')
        !Files.exists(excludedFactory) // This is the failing line

        // Expect NO factory for 'com.other'
        def otherFactory = outputDir.toPath().resolve('com/other/ImportFactory.java')
        !Files.exists(otherFactory)

        // Assert that the generated directory does not contain unexpected files
        def generatedFiles = []
        outputDir.eachFileRecurse { file -> // outputDir is now a File, so eachFileRecurse works
            if (file.isFile()) {
                generatedFiles << file.absolutePath
            }
        }
        !generatedFiles.any { it.contains('com/included/excluded/ImportFactory.java') }
        !generatedFiles.any { it.contains('com/other/ImportFactory.java') }
    }

    private File createTestJar(String name, List<String> entries) {
        def jarFile = tempDir.resolve(name).toFile()

        def manifest = new Manifest()
        manifest.getMainAttributes().putValue('Manifest-Version', '1.0')

        new JarOutputStream(new FileOutputStream(jarFile), manifest).withCloseable { jos ->
            // Track added directories to avoid duplicates
            def addedDirs = new HashSet<String>()

            entries.each { entry ->
                // Add package directories first
                def parts = entry.split('/')
                def currentPath = ""
                for (int i = 0; i < parts.length - 1; i++) {
                    currentPath += parts[i] + "/"
                    if (addedDirs.add(currentPath)) {
                        jos.putNextEntry(new JarEntry(currentPath))
                        jos.closeEntry()
                    }
                }

                // Add class file with minimal valid class file content
                jos.putNextEntry(new JarEntry(entry))
                // Create a more complete minimal class file
                def classContent = createMinimalClassFile()
                jos.write(classContent)
                jos.closeEntry()
            }
        }
        return jarFile
    }

    // A minimal Java class file for testing purposes.
    // This represents a simple class like `public class SomeClass {}` compiled with Java 8.
    private byte[] createMinimalClassFile() {
        return [
                // Magic number
                0xCA, 0xFE, 0xBA, 0xBE,
                // Minor version
                0x00, 0x00,
                // Major version (Java 8 = 52 = 0x34)
                0x00, 0x34,
                // Constant pool count (adjusted to include class and utf8 for class name)
                0x00, 0x07, // Increased constant pool size for a simple class

                // CP entry 1: MethodRef (java/lang/Object.<init>:()V) - for default constructor
                0x0A, 0x00, 0x03, 0x00, 0x04,

                // CP entry 2: Class_info for java/lang/Object
                0x07, 0x00, 0x05,

                // CP entry 3: Class_info for "SomeClass" (placeholder)
                0x07, 0x00, 0x06,

                // CP entry 4: NameAndType for <init> ()V
                0x0C, 0x00, 0x07, 0x00, 0x08,

                // CP entry 5: UTF8 "java/lang/Object"
                0x01, 0x00, 0x10,
                // 'j', 'a', 'v', 'a', '/', 'l', 'a', 'n', 'g', '/', 'O', 'b', 'j', 'e', 'c', 't'
                // Corrected byte values for "java/lang/Object"
                (byte) 'j', (byte) 'a', (byte) 'v', (byte) 'a', (byte) '/',
                (byte) 'l', (byte) 'a', (byte) 'n', (byte) 'g', (byte) '/',
                (byte) 'O', (byte) 'b', (byte) 'j', (byte) 'e', (byte) 'c', (byte) 't',


                // CP entry 6: UTF8 "SomeClass" (will be replaced by actual class name in JAR entry)
                0x01, 0x00, 0x09,
                // 'S', 'o', 'm', 'e', 'C', 'l', 'a', 's', 's'
                // Corrected byte values for "SomeClass"
                (byte) 'S', (byte) 'o', (byte) 'm', (byte) 'e',
                (byte) 'C', (byte) 'l', (byte) 'a', (byte) 's', (byte) 's',

                // CP entry 7: UTF8 "<init>"
                0x01, 0x00, 0x06,
                // '<', 'i', 'n', 'i', 't', '>'
                // Corrected byte values for "<init>"
                (byte) '<', (byte) 'i', (byte) 'n', (byte) 'i', (byte) 't', (byte) '>',

                // CP entry 8: UTF8 "()V"
                0x01, 0x00, 0x03,
                // '(', ')', 'V'
                // Corrected byte values for "()V"
                (byte) '(', (byte) ')', (byte) 'V',

                // Access flags (ACC_PUBLIC | ACC_SUPER)
                0x00, 0x21,
                // This class (index 3 points to "SomeClass" placeholder)
                0x00, 0x03,
                // Super class (index 2 points to java/lang/Object)
                0x00, 0x02,
                // Interfaces count
                0x00, 0x00,
                // Fields count
                0x00, 0x00,
                // Methods count (for default constructor)
                0x00, 0x01,
                // Methods[0]: <init> method
                0x00, 0x01, // Access flags (ACC_PUBLIC)
                0x00, 0x07, // Name index (<init>)
                0x00, 0x08, // Descriptor index (()V)
                0x00, 0x01, // Attributes count (Code attribute)
                // Code attribute
                0x00, 0x09, // Attribute name index (UTF8 "Code") - assuming it's CP index 9
                0x00, 0x00, 0x00, 0x11, // Attribute length
                0x00, 0x01, // Max stack
                0x00, 0x01, // Max locals
                0x00, 0x00, 0x00, 0x05, // Code length
                0x2A,       // aload_0
                0xB7, 0x00, 0x01, // invokespecial (java/lang/Object.<init>)
                0xB1,       // return
                0x00, 0x00, // Exception table length
                0x00, 0x00, // Attributes count for Code attribute

                // Attributes count for the class itself
                0x00, 0x00
        ] as byte[]
    }
}