# Micronaut Gradle Plugin Docs Layout

This reference captures repository-specific facts that replace the standard Micronaut module docs guidance from `micronaut-project-template`.

## Guide source

- Primary guide source: `src/docs/asciidoc/index.adoc`
- There is no `src/main/docs/guide/toc.yml` in this repository.
- Shared assets are stored under `src/docs/asciidoc/css/`, `src/docs/asciidoc/js/`, `src/docs/asciidoc/highlight/`, and `src/docs/asciidoc/template/`.

Maintainer implication:

- Do not add or update `src/main/docs/guide/toc.yml` for normal Micronaut Gradle Plugin guide changes.
- Keep section ordering and anchors in the single `index.adoc` file unless the requested docs change explicitly reorganizes the guide.

## Docs build wiring

Repository-local build logic:

- `buildSrc/src/main/groovy/io.micronaut.internal.build.documented.gradle`

Relevant behavior:

- Applies `org.asciidoctor.jvm.convert`.
- Configures the `asciidoctor` task output under `build/asciidoc`.
- Copies local CSS, JavaScript, and highlight assets from `src/docs/asciidoc` as `asciidoctor` resources.
- Registers `asciidocThemer` to read and apply `src/docs/asciidoc/template/template.html`.
- Registers `docs`, a `Copy` task that writes assembled documentation under `build/docs` and includes generated API docs under `build/docs/api`.

Maintainer implication:

- Use `./gradlew docs` for guide rendering verification.
- Inspect `build/docs/` when the visual/rendered output matters.

## Source-backed examples

The guide currently uses plain Asciidoctor includes for source-backed examples. Examples include:

- `include::samples/test-resources/custom-test-resource/src/testResources/java/demo/GreetingTestResource.java[]`
- `include::samples/test-resources/custom-test-resource/src/testResources/resources/META-INF/services/io.micronaut.testresources.core.TestResourcesResolver[]`
- `include::samples/test-resources/custom-test-resource/src/test/java/demo/DemoTest.java[tag=test]`

Sample projects live under `samples/` and are copied into TestKit builds by:

- `minimal-plugin/src/testFixtures/groovy/io/micronaut/gradle/AbstractGradleBuildSpec.groovy`

Maintainer implication:

- Preserve includes and snippet tags when editing nearby prose.
- If a sample-backed snippet needs to change, update the sample source and any TestKit coverage that depends on it.
- Do not paste generated snippet output into the guide as a replacement for a source include.

## Template docs macros not imported by default

The standard Micronaut module docs skill references `dependency:`, `snippet::`, `[configuration]`, generated configuration property includes, and `publishGuide`. Those are provided by the normal Micronaut module docs pipeline.

This repository's documented pipeline is different:

- It has a single Gradle Plugin guide in `src/docs/asciidoc/index.adoc`.
- Its documented verification task is `./gradlew docs`.
- Existing source-backed examples use plain Asciidoctor `include::` directives and tags.

Maintainer implication:

- Do not introduce template-only docs macros unless the local build is explicitly updated and verified to support them.
- Prefer the local include/tag style for narrow docs changes.

## Release and publishing caveat

The Micronaut Gradle Plugin repository publishes Gradle plugins and plugin marker metadata through the Gradle Plugin Portal. It is not a standard Micronaut module release.

Maintainer implication:

- Do not copy Maven Central-only or standard module-release docs guidance from `micronaut-project-template` into this repository.
- When documenting release or publishing behavior, check `.github/workflows/release.yml`, `MAINTAINING.md`, module `build.gradle` plugin marker registrations, and current Gradle Plugin Portal conventions.
