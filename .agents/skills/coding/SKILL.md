---
name: coding
description: Implement and review Java, Groovy, Kotlin, and Gradle plugin changes for the Micronaut Gradle Plugin repository using maintainer standards. Use when users ask to add or refactor plugin code, fix repository bugs, evolve internal APIs, or prepare committer-ready changes with tests and verification.
license: Apache-2.0
compatibility: micronaut-projects/micronaut-gradle-plugin
metadata:
  author: Álvaro Sánchez-Mariscal
  version: "1.0.0"
---

# Coding (Micronaut Committer)

Use this skill for maintainer-facing implementation work in the Micronaut Gradle Plugin repository. Do not default to end-user application shortcuts or standard Micronaut module assumptions.

## Goal

Deliver minimal, source-backed changes that preserve Gradle Plugin quality: plugin behavior compatibility, repository nullability conventions, configuration-cache-friendly lazy Gradle APIs, TestKit coverage, and focused Gradle verification.

## Trigger Examples

Should trigger:

- "Implement this Micronaut Gradle Plugin feature in `src/main/java` and keep API compatibility."
- "Refactor plugin internals and keep Gradle configuration lazy."
- "Fix failing TestKit or functional tests and prepare committer-ready validation output."
- "Add extension or task configuration support using repository conventions, not app-level shortcuts."

Should not trigger:

- "Explain Micronaut basics to a beginner."
- "Create an end-user sample app from scratch."
- "Only edit release notes/changelog text."

## Procedure

1. Establish scope and API impact.
2. Implement code with Micronaut Gradle Plugin maintainer conventions.
3. Enforce API boundaries and binary compatibility.
4. Document Java APIs and implementation intent.
5. Keep Gradle/build changes aligned with repository conventions.
6. Verify with maintainer-grade checks before completion.

### 1) Establish scope and API impact

- Identify affected modules and whether any change is public API or internal-only.
- Inspect existing package patterns before editing (imports, nullability style, tests, naming).
- For API-facing edits, plan compatibility checks up front (`japiCmp`).
- Keep change surface minimal; avoid opportunistic refactors unless required.

### 2) Implement code with Micronaut Gradle Plugin maintainer conventions

- Prefer modern Java idioms where they improve clarity (records, sealed types, pattern matching, `var` for local inference), but only when supported by the repository toolchain/target level.
- Do not use fully qualified class names unless import conflicts force it.
- Preserve the repository's existing nullability style in the package you edit. This repository currently uses legacy nullable annotations in some Java and Groovy sources; introduce JSpecify only when the dependency/toolchain is already available or the approved change explicitly adds a repository-wide nullability migration.
- If JSpecify is available and the approved change uses it, new Java packages should include `package-info.java` with `@NullMarked` and `import org.jspecify.annotations.NullMarked`; use `org.jspecify.annotations.Nullable` for nullable values, including nullable parameters, return values, fields, array/component positions such as `String @Nullable []`, nullable collection elements such as `List<@Nullable T>`, and nullable type bounds such as `<T extends @Nullable Object>`.
- Preserve existing nullability intent when editing older code. Do not rewrite deliberate compatibility annotations such as `javax.annotation.Nullable`, `io.micronaut.core.annotation.Nullable`, or `jakarta.annotation.Nullable` unless the task is specifically a nullability migration and compatibility impact has been checked.
- Avoid reflection-oriented implementations in plugin code paths; prefer Gradle providers, Micronaut compile-time/introspection mechanisms, and existing repository helpers.
- Use `javax.inject.Inject` for Gradle-managed service/object injection in plugin production code, matching existing Gradle API conventions in this repository.
- Use `jakarta.inject` in generated or sample Micronaut application/library code where application DI APIs require it.
- Prefer constructor injection and immutable state over field injection.
- For Micronaut application configuration models in samples or generated code, prefer `@ConfigurationProperties` over scattered `@Value` usage.

### 3) Enforce API boundaries and compatibility

- Treat all public-facing changes through a Semantic Versioning lens (`https://semver.org/`) before implementation.
- Classify impact explicitly: patch for backward-compatible fixes, minor for backward-compatible feature additions, major for breaking API/behavioral changes.
- Keep public API binary compatible unless a major-version change explicitly allows breaks.
- Prefer non-breaking API evolution first: deprecate existing methods and add replacement variants/overloads instead of deleting methods or changing signatures in place.
- When using the deprecate-and-add path, keep deprecated APIs functional, point to replacements in Javadoc, and schedule removals only for the next major version.
- If breaking public-facing changes are explicitly allowed, document them in `src/docs/asciidoc/index.adoc` with migration notes and update runnable samples or TestKit coverage when examples change. This repository uses a single-file AsciiDoc guide, not the framework guide/toc layout.
- Mark non-user-facing APIs with `@io.micronaut.core.annotation.Internal`.
- Mark unstable public APIs with `@io.micronaut.core.annotation.Experimental` and avoid presenting them as stable contracts.
- Mark members directly called by generated code with `@io.micronaut.core.annotation.UsedByGeneratedCode`; preserve those signatures unless the generated-code callers are updated in the same change.
- Keep visibility as narrow as possible for non-public internals.
- When deprecating API, provide migration-friendly Javadoc and avoid silent behavioral breaks.

### 4) Document Java APIs and implementation intent

- All public Java types and public methods must have Javadoc. Include public constructors when they are part of the user-facing API.
- All new public Java types, methods, and user-facing constructors must include an `@since` Javadoc tag for the version where the PR will debut.
- Determine the `@since` version from the approved target branch, not from guesswork. Inspect that branch's `gradle.properties` (`projectVersion`) and use the corresponding release version; for example, `4.9.0-SNAPSHOT` means the new API debuts in `4.9.0`.
- If the PR is retargeted during follow-through, re-check the target branch's `gradle.properties` and update any newly added `@since` tags when the debut version changes.
- Do not add `@since` tags to internal-only APIs annotated with `@Internal` unless the repository already does so for that internal package.
- Internal and package-private methods should include maintainer-focused Javadoc when the implementation contract, lifecycle, invariants, or generated-code interaction is not obvious from the signature.
- Complex new code should include focused inline comments that explain implementation decisions or invariants; do not narrate straightforward control flow.

### 5) Keep Gradle/build changes convention-aligned

- Use `./gradlew` for all Gradle execution.
- Use Gradle version catalogs (`gradle/libs.versions.toml`) instead of hard-coded dependency versions.
- Use appropriate scopes (`api`, `implementation`, `compileOnly`, `runtimeOnly`) based on API exposure.
- Do not add custom build logic directly in module build files when it belongs in convention plugins.
- When uncertain about module paths, use `./gradlew projects` and prefer canonical `micronaut-*` project names.
- Keep plugin behavior lazy and configuration-cache friendly; do not add network calls, Docker access, or native-image execution during Gradle configuration.

### 6) Verify before completion

First confirm canonical verification tasks from `AGENTS.md`, `MAINTAINING.md`, and existing CI/build files, then run the repository equivalents from root.

Common sequence in this repository:

```bash
./gradlew :<module>:compileTestJava
# If module includes Groovy tests:
./gradlew :<module>:compileTestGroovy
./gradlew :<module>:test --tests 'pkg.ClassTest'
./gradlew :<module>:test
# If repository documents cM alias/checkstyle aggregate task:
./gradlew -q cM
./gradlew -q spotlessCheck
./gradlew check
./gradlew docs
```

For API-affecting changes, also run if configured in the repository:

```bash
./gradlew japiCmp
```

If Spotless fails, run `./gradlew -q spotlessApply` and re-run `spotlessCheck`.

## Guardrails

- Do not replace Gradle plugin production-code `javax.inject.Inject` usage with `jakarta.inject` unless the repository's Gradle API conventions explicitly change.
- Do not introduce new nullability annotation families unless the repository already uses them or the approved change explicitly adds them.
- Do not hard-code dependency versions in module build files.
- Do not break public APIs without explicit major-version intent.
- Do not skip tests or docs verification for code changes.
- Do not use reflection as a convenience in plugin internals.

## Delivery Contract

When finishing implementation work, report:

1. Exactly which files changed and why.
2. Whether the change is API-facing or internal-only.
3. Semantic Versioning impact classification (patch/minor/major) for any public-facing change.
4. For deprecate-and-add API evolution, which elements were deprecated and which replacement variants were introduced.
5. For breaking public-facing changes, which user guide files were updated and what migration guidance was added.
6. For new public Java APIs, the `@since` version used and how it was derived from the target branch's `gradle.properties`.
7. Commands executed for verification and outcomes.
8. Any follow-up risk (for example compatibility implications).

## Validation Checklist

- [ ] `SKILL.md` frontmatter is valid and `name` matches directory (`coding`).
- [ ] Guidance is maintainer-focused (not end-user app guidance).
- [ ] Java conventions preserve repository nullability style and use JSpecify only when available or explicitly added.
- [ ] API boundary guidance includes `@Internal`, `@Experimental`, `@UsedByGeneratedCode`, and compatibility checks.
- [ ] Public Java types and methods require Javadoc.
- [ ] New public Java APIs require `@since` tags derived from the approved target branch's `gradle.properties`.
- [ ] Internal/package-private methods and complex new code include maintainer-focused Javadoc or inline comments when implementation intent is not obvious.
- [ ] For public API evolution without breaking changes, deprecations include clear replacement guidance and functional compatibility is preserved.
- [ ] If breaking changes are allowed, `src/docs/asciidoc/index.adoc` is updated with migration notes.
- [ ] Verification includes tests, style checks, `check`, and `docs`.

## References

- `AGENTS.md`
- `MAINTAINING.md`
- `src/docs/asciidoc/index.adoc`
- `.github/workflows/gradle.yml`
- `.github/workflows/release.yml`
