---
name: docs
description: Maintain Micronaut Gradle Plugin user guide documentation and docs examples. Use when changing src/docs/asciidoc/index.adoc, docs assets, samples referenced by the guide, or docs build/publishing behavior.
license: Apache-2.0
compatibility: micronaut-projects/micronaut-gradle-plugin
metadata:
  author: Micronaut Agent Company
  version: "1.0.0"
  source: micronaut-projects/micronaut-project-template .agents/skills/docs adapted for the Gradle Plugin repository
---

# Docs (Micronaut Gradle Plugin Maintainer)

Use this skill for maintainer-facing documentation work in the Micronaut Gradle Plugin repository. The repository does **not** use the standard Micronaut module `src/main/docs/guide/toc.yml` layout; keep the local single-page AsciiDoc guide and Gradle Plugin-specific docs pipeline authoritative.

## Goal

Implement source-backed documentation changes in `src/docs/asciidoc/index.adoc`, keep sample snippets and assets aligned with executable repository sources, and validate with this repository's `docs` task.

## Procedure

1. Confirm the local docs layout and changed surface.
2. Edit the single AsciiDoc guide and any source-backed samples together.
3. Preserve repository-specific snippet/include conventions.
4. Keep Gradle Plugin behavior, task names, and release/publishing guidance accurate.
5. Build and inspect documentation output.

### 1) Confirm docs layout and changed surface

- Start with `src/docs/asciidoc/index.adoc`; it is the guide source of truth for this repository.
- There is no `src/main/docs/guide/toc.yml` in this repository. Do not add one for normal guide work.
- Static guide assets live under:
  - `src/docs/asciidoc/css/`
  - `src/docs/asciidoc/js/`
  - `src/docs/asciidoc/highlight/`
  - `src/docs/asciidoc/template/`
- Runnable documentation examples live under `samples/` and are copied into TestKit builds by `minimal-plugin/src/testFixtures/groovy/io/micronaut/gradle/AbstractGradleBuildSpec.groovy`.
- Docs build wiring is repository-local in `buildSrc/src/main/groovy/io.micronaut.internal.build.documented.gradle`.

### 2) Edit guide and samples together

- When prose describes a plugin, extension, task, default, or generated file, confirm the implementation or tests in the owning module before editing.
- When a code block comes from a sample or test fixture, prefer updating the source sample and preserving an include over pasting a divergent copy into the guide.
- If a sample changes, run the focused TestKit/spec coverage when practical, or document why only docs rendering was run.
- Keep public task, extension, and plugin-id names exact; this repository publishes Gradle plugins and plugin marker metadata.

### 3) Preserve snippet/include conventions

This repository uses plain Asciidoctor includes and tagged source snippets rather than the standard Micronaut module `snippet::` and `dependency:` macros.

Guardrails:

- Preserve existing `include::...[]` snippets, tags such as `tag=test`, and callout explanations unless the underlying source behavior changes.
- Do not convert source-backed includes into pasted `[source]` blocks as a cleanup shortcut.
- Do not introduce Micronaut module-only docs macros (`dependency:`, `snippet::`, generated configuration property includes, or `toc.yml`) unless the local docs build is first updated and verified to support them.
- Keep multi-language or multi-build-tool examples consistent with the current guide style and samples.
- Keep environment-sensitive instructions explicit, especially for Docker, GraalVM/native-image, CRaC, AOT, and test resources.

See `references/gradle-plugin-docs.md` for repository-specific source evidence.

### 4) Keep Gradle Plugin-specific behavior accurate

- Preserve Gradle Plugin repository layout, wrapper policy, release automation, and Gradle Plugin Portal publishing model.
- Do not copy Maven Central-only release or standard Micronaut module publishing advice from template docs.
- When documenting Gradle/JDK compatibility, use the support matrix encoded in this repository and preserve user-facing unsupported-version guidance.
- When documenting plugin application, dependency resolution, buildscript behavior, Docker/native-image behavior, or publishing metadata, add or update focused tests whenever possible.
- Keep examples compatible with the targeted release line unless an approved issue plan says otherwise.

### 5) Build and validate documentation

For docs-only guide changes, run:

```bash
./gradlew docs
```

Validation checklist:

1. `./gradlew docs` exits with code `0`.
2. Rendered output exists under `build/docs/`.
3. Updated sections render without missing includes or broken local assets.
4. `git diff --check` passes.

For broad sample or behavior changes, also run the focused module tests or functional TestKit specs that exercise the changed example.

## Maintainer Delivery Contract

When finishing docs work, report:

1. Exact changed files (`index.adoc`, samples, docs resources, or docs build logic).
2. Which source-backed includes or examples were preserved or updated.
3. Build/test commands run and outcomes.
4. Any repo-specific divergence from Micronaut Project Template docs guidance.
5. Any skipped template docs changes and why they are not applicable to the Gradle Plugin repository.

## Validation Checklist

- [ ] `src/docs/asciidoc/index.adoc` remains the guide source of truth.
- [ ] Existing includes, tags, and callouts are preserved unless source behavior changed.
- [ ] No unsupported Micronaut module docs macros or `toc.yml` were introduced.
- [ ] `./gradlew docs` and `git diff --check` ran successfully, or skipped validation has a concrete blocker.
- [ ] Guidance remains Gradle Plugin-maintainer focused, not generic application documentation.

## References

- `references/gradle-plugin-docs.md`
- `AGENTS.md`
- `src/docs/asciidoc/index.adoc`
- `buildSrc/src/main/groovy/io.micronaut.internal.build.documented.gradle`
