# Micronaut Gradle Plugin Agent Guide

Use this file as durable project context for automation and review work in this repository. Keep changes scoped to the relevant plugin module and prefer local verification over broad, slow builds unless behavior changes cross module boundaries.

## Module Map

- `minimal-plugin`: shared Micronaut Gradle extension, runtime/test-runtime selection, annotation processing, Kotlin support, version lookup, and the `io.micronaut.minimal.application` / `io.micronaut.minimal.library` plugins.
- `gradle-plugin`: high-level `io.micronaut.application` and `io.micronaut.library` plugins that compose minimal, GraalVM, Docker, and Eclipse annotation-processing behavior.
- `docker-plugin`: Docker image, Dockerfile, native Docker image, CRaC image integration points, layer tasks, and Dockerfile editing helpers.
- `graalvm-plugin`: GraalVM native-build-tools integration, native test/build conventions, native Lambda support, and native-image argument wiring.
- `aot-plugin`: Micronaut AOT tasks, optimized runner jars/distributions, optimized Docker/native Docker behavior, and AOT sample configuration generation.
- `test-resources-plugin`: `io.micronaut.test-resources` and `io.micronaut.test-resources-consumer`, test resources service lifecycle, GraalVM/AOT integration, and standalone or multi-project sharing.
- `openapi-plugin`, `jsonschema-plugin`, `configuration-validation-plugin`, and `openrewrite-plugin`: code generation, configuration validation, and migration-helper plugins with their own extensions and task types.
- `platform-catalog-plugin`: settings plugin for importing the Micronaut Platform version catalog.
- `crac-plugin`: CRaC checkpoint/final Docker image support and generated helper scripts.
- `functional-tests`: cross-plugin TestKit coverage, including Kotlin, Docker, GraalVM, AOT, OpenAPI, configuration validation, and test resources scenarios.
- `buildSrc`: repository build conventions and test wiring.

## Where To Look

- Plugin ids are registered in each module's `build.gradle`; start there before changing plugin behavior.
- Public user documentation is in `src/docs/asciidoc/index.adoc`. This repo does not use `src/main/docs/guide/toc.yml`; the guide is a single AsciiDoc file plus shared CSS/JS/template assets under `src/docs/asciidoc`.
- Runnable documentation examples live under `samples/` and are copied into TestKit builds by `minimal-plugin/src/testFixtures/groovy/io/micronaut/gradle/AbstractGradleBuildSpec.groovy`.
- Docker behavior lives under `docker-plugin/src/main/java/io/micronaut/gradle/docker` and is exercised by both `docker-plugin/src/test` and `functional-tests/src/test/groovy/io/micronaut/gradle/docker`.
- GraalVM behavior is split between `graalvm-plugin`, shared helpers in `minimal-plugin/src/main/java/io/micronaut/gradle/graalvm`, and native functional tests under `functional-tests/src/test/groovy/io/micronaut/gradle/graalvm` and `.../lambda`.
- Test resources behavior is in `test-resources-plugin/src/main/java/io/micronaut/gradle/testresources`, with sample projects under `samples/test-resources`.
- OpenAPI and JSON Schema generation task behavior is in `openapi-plugin/src/main/java/io/micronaut/gradle/openapi` and `jsonschema-plugin/src/main/java/io/micronaut/gradle/jsonschema`; Kotlin/OpenAPI integration tests are under `functional-tests/src/test/groovy/io/micronaut/gradle/openapi`.

## Verification

- Documentation-only guidance changes: run `git diff --check` and do a readability pass.
- Single module changes: prefer the focused module test task, for example `./gradlew :micronaut-docker-plugin:test` or `./gradlew :micronaut-openapi-plugin:test`.
- Cross-plugin behavior changes: run the relevant focused module tests plus targeted `:functional-tests:test --tests ...` coverage.
- User guide changes: run `./gradlew docs` when AsciiDoc, samples, generated API links, or guide assets change.
- Before release-sensitive changes, check `.github/workflows/gradle.yml`, `.github/workflows/release.yml`, and `MAINTAINING.md` for current CI, docs publishing, and release behavior.

## CI And Release Notes

- The main CI workflow runs `./gradlew check --no-daemon --continue` on pull requests and pushes to `master` or maintenance branches.
- Docs are generated into `build/docs` and published by the CI/release workflows, not by a checked-in release-notes file.
- Several workflow files are synchronized from `micronaut-project-template`; avoid repo-local workflow edits unless the change intentionally opts out of template sync.
- Docker, native-image, and test resources tests can depend on local Docker or GraalVM availability. Use existing availability guards in `AbstractGradleBuildSpec` and the CI GraalVM workflows instead of adding unconditional environment assumptions.

## Repo-Specific Anti-Patterns

- Do not add network calls, Docker access, or native-image execution during Gradle configuration; keep plugin logic lazy and configuration-cache friendly.
- Do not make high-level application/library plugins duplicate logic from `minimal-plugin`, `docker-plugin`, or `graalvm-plugin`; compose plugin behavior through existing extension and plugin hooks.
- Do not hard-code generated output paths in tests when task properties expose the location.
- Do not update generated docs prose without aligning the corresponding sample, TestKit coverage, or public extension/task API.
- Do not edit synchronized workflow files casually; prefer build logic, setup hooks, or non-synchronized workflows when repo-specific behavior is required.
