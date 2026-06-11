# Qiq Templates Support Plugin

[![Version](https://img.shields.io/jetbrains/plugin/v/28576)](https://plugins.jetbrains.com/plugin/28576)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28576)](https://plugins.jetbrains.com/plugin/28576)

Qiq Templates Support is an IntelliJ-based plugin that brings syntax highlighting, navigation, PHP injection, and other IDE niceties to [Qiq templates](https://qiqphp.com). It targets JetBrains IDEs that can host the PhpStorm platform (PhpStorm, IntelliJ Ultimate with PHP plugin, etc.).

## Features

- **Syntax highlighting** for Qiq control structures, escape modifiers (`{{= }}`, `{{h }}`, etc.), and HTML blocks.
- **PHP language injection** inside Qiq tokens and inline `<?php ?>` islands so that autocompletion, inspections, and formatting work as expected.
- **Type-aware escape directives**: `{{h }}`, `{{a }}`, `{{j }}`, `{{u }}`, `{{c }}` are routed through typed runtime stubs so PhpStorm flags wrong argument types (`{{h $array }}`, `{{h $objectWithoutToString }}`, etc.).
- **Composer-aware stub selection**: the strict (Qiq 1.x) or relaxed (Qiq 2.x / 3.x) escape signature is chosen automatically from `composer.lock`.
- **Cross-template rename refactoring**: renaming a PHP property, method, or local variable propagates into every Qiq template that references it. Triggering Shift+F6 from inside a Qiq template (`{{h $article->title }}`) also works.
- **Cross-template navigation**: Cmd/Ctrl+click (Go to Declaration) on `setLayout()`, `render()`, `include()`, or custom helpers to open the referenced template file.
- **Helper Go to Declaration**: Cmd/Ctrl+click on a helper call (`{{ helperName(...) }}` / `{{ $this->helperName(...) }}`) jumps to its PHP declaration. Qiq 2.x/3.x helpers (public methods on a `Qiq\Helpers` subclass) are discovered automatically; Qiq 1.x `HelperLocator::set()` helpers resolve once you point the plugin at your registration file in settings. Resolvable helper calls also stop triggering the "undefined function/method" warning.
- **Helper completion & code insight**: custom helper names complete at call positions (1.x `HelperLocator` registrations + 2.x/3.x `Qiq\Helpers` methods, case-insensitive). Helper calls get parameter info (Cmd/Ctrl+P), Quick Documentation (Cmd/Ctrl+Q), and an argument inspection that flags the wrong argument count or a positional type mismatch (honoring the strict-types option for `null`).
- **Template path completion & validation**: the path argument of `setLayout()`, `render()`, `extends()`, and `include()` completes from the discovered template roots — both relative paths and Qiq root-absolute paths (`/layout/base`, leading `/` optional), matched as substrings. A path that resolves to no template is flagged with a warning.
- **Code folding** for Qiq block directives (`{{ if (...): }}`…`{{ endif }}`, `foreach`, `for`, `setSection`, `setBlock`) — only the body folds, so the opener and closer stay visible.
- **Block matching & pair highlighting**: Qiq delimiters (`{{`, `{{=`, `{{h`, etc.) pair with their closing `}}` without matching braces from injected PHP or HTML, and the caret on a block opener/closer highlights its partner directive — nested same-keyword blocks resolve correctly.
- **Structure view**: the Structure tool window (Alt+7 / Cmd+7) outlines sections/blocks by name, nested control blocks, and top-level `setLayout()`/`extends()` directives, with click-to-navigate.
- **Block directive inspection**: warns on an unclosed opener, a closer with no opener, and a closer that doesn't match the nearest open block (`{{ foreach }}`…`{{ endif }}`).
- **Section names as symbols**: section names complete in `getSection('…')` / `hasSection('…')` from the `setSection` definitions across the template roots; Go to Declaration jumps both ways (reader → definitions, `setSection` → usages, with file/line shown), even when a name collides with a PHP function; an inspection flags a `getSection('x')` that no template defines. `$this->`-qualified directives (`$this->setSection(...)` … `$this->endSection()`) fold, pair, resolve, and argument-check like the bare form.
- **Enter/typing handlers** that auto-complete Qiq block closers and keep indentation consistent.
- **Template discovery**: resolves relative paths by walking up from the current file, project roots, and PHP server document roots.

## Settings

Open **Settings (Preferences) → Languages & Frameworks → Qiq Templates** for project-level options:

- **Inject `declare(strict_types=1)` into Qiq templates** *(off by default)* — when enabled, scalar literal misuses such as `{{h true }}`, `{{h 123 }}`, or `{{h null }}` surface as PhpStorm type warnings. Useful when your project renders templates under PHP strict types; off by default to match Qiq's runtime, which performs implicit scalar→string casts.
- **Helper Locator bootstrap files** — for Qiq 1.x projects, list the PHP file(s) that register helpers via `$locator->set('name', ...)`. The plugin scans them to resolve helper calls for Go to Declaration. Not needed for Qiq 2.x/3.x, whose `Qiq\Helpers` subclasses are discovered automatically.

## Installation

The project uses Gradle with the JetBrains IntelliJ Platform Plugin tooling.

### Build a distributable ZIP

```bash
./gradlew buildPlugin
```

The packaged plugin will be at `build/distributions/QiqTemplateSupport-<version>.zip`.

### Run in a sandbox IDE

```bash
./gradlew runIde
```

This downloads the specified PhpStorm platform, installs the plugin into a sandbox, and launches the IDE for manual testing.

## Development

### Prerequisites

- JDK 21+
- Gradle (wrapper included)
- A JetBrains IDE for Kotlin/Java development (recommended)

### Project structure

```
src/main/kotlin/
  io/github/jingu/idea_qiq_plugin/
    lexer/          # JFlex definition and generated lexer adapter
    highlight/      # Syntax highlighters, colors, annotators
    inject/         # PHP language injection
    navigation/     # Reference contributors and resolution utilities
    editor/         # Typed/Enter handlers
    util/           # Path resolution helpers
    ...             # Other plugin components
src/main/resources/
  META-INF/plugin.xml   # Plugin descriptor
  messages/             # Resource bundle for UI strings
  ...
src/test/kotlin/        # Unit tests
README.md
```

Generated lexer sources are placed under `src/gen/` (ignored by Git). To regenerate them after editing `Qiq.flex` run:

```bash
./gradlew generateQiqLexer
```

### Running tests

```bash
./gradlew test
```

Tests run against the PhpStorm platform specified in `build.gradle.kts`.

### Coding guidelines

- Kotlin sources follow JetBrains' official style.
- Keep packages under `io.github.jingu.idea_qiq_plugin` aligned with directory layout.
- Use ASCII characters unless non-ASCII is necessary.
- Before committing, consider running `./gradlew test` and ensure the sandbox IDE behaves as expected for major UI changes.

## Contributing

Pull requests and issue reports are welcome. Please include:

- A clear description of the change or bug.
- Steps to reproduce bugs when applicable.
- Added or updated tests covering new functionality.

## License

This project is released under the MIT License. See [LICENSE](LICENSE) for details.
