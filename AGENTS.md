# Repository Guidelines

## Project Structure & Module Organization
Kotlin sources live in `src/main/kotlin/io/github/jingu/idea_qiq_plugin`, grouped by feature packages (lexer, highlight, navigation, editor). Generated lexer code lands in `src/gen/io/github/jingu/idea_qiq_plugin`; never edit those files—update `src/main/kotlin/io/github/jingu/idea_qiq_plugin/lexer/Qiq.flex` instead. Plugin descriptors, bundle messages, icons, and bundled PHP helpers reside in `src/main/resources`. Sample Qiq templates (`test_*.qiq`) sit in the repo root for manual checks.

## Build, Test, and Development Commands
Use `./gradlew clean build` to compile sources, regenerate the lexer, and package the plugin. Run `./gradlew test` for JVM/Kotlin tests under `src/test/kotlin`. Launch a sandbox IDE with `./gradlew runIde` for interactive testing. After lexer edits, run `./gradlew generateQiqLexer` to refresh generated sources. Produce a ZIP with `./gradlew buildPlugin`, and validate it with `./gradlew verifyPlugin`.

## Coding Style & Naming Conventions
Follow JetBrains’ Kotlin style: 4-space indentation, braces on the same line, `PascalCase` classes, and `camelCase` functions or properties. Keep packages aligned with the directory layout under `io.github.jingu.idea_qiq_plugin`. Use IntelliJ’s “Reformat Code” before committing, and keep `plugin.xml`, `php.xml`, and message bundle keys sorted. When touching PHP helper scripts, match the current PSR-12-ish formatting.

## Testing Guidelines
Place unit or fixture-based tests in `src/test/kotlin`, mirroring the main source packages. Store supporting templates in `src/test/resources` or reuse the root `test_*.qiq` fixtures. Name tests after the feature under test (e.g., `QiqPhpInjectorTest`). Ensure lexer/parser changes include regression coverage and re-run `./gradlew test` before opening a pull request. Manual sandbox validation should accompany changes affecting editing, completion, or highlighting.

## Commit & Pull Request Guidelines
Commit messages follow a `<type>: <brief summary>` pattern (`feat`, `refactor`, `fix`, etc.). Keep commits focused on a single concern and include generated files only when necessary for CI stability. Pull requests should link issues, describe behavioral changes, and call out UI impacts with screenshots or screencasts. Note manual QA steps (e.g., sandbox IDE scenarios) so reviewers can reproduce them quickly.

## Architecture Overview
`QiqParserDefinition`, `QiqLexerAdapter`, and `_QiqLexer.java` define the language surface, while `QiqSyntaxHighlighter` and `QiqPhpInjector` drive highlighting and PHP embedding. `QiqSettingService` discovers template roots at runtime; update it whenever search rules evolve. Navigation hooks in `navigation/*` rely on accurate path resolution from `QiqPathReference`, so adjust tests and fixtures alongside behavioral changes.

## IntelliJ Plugin Tips
Ensure the PHP bundled plugin dependency remains declared in `build.gradle.kts` and `php.xml`; the Qiq integration assumes PHP PSI is available. After updating JetBrains platform versions, re-run `./gradlew runIde` to confirm inspections, syntax highlighting, and template resolution still behave as expected.
