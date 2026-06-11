<?php
/**
 * Qiq runtime stubs (covers 1.x–3.x)
 * - These stubs exist for IDE indexing/completion only.
 * - Functions are defined to satisfy calls inside {{ ... }} blocks.
 * - A minimal QiqTemplate class exposes equivalent methods for $this->...() calls.
 * - Names are case-insensitive at runtime; one definition is sufficient.
 */

/* ---------------------------
 * Template control (functions)
 * --------------------------- */
function render(string $path, ...$args): string {}
function setLayout(string $path): void {}
function getContent(): string { return ''; }

/* ---------------------------
 * Blocks (functions)
 * --------------------------- */
function setBlock(string $name): void {}
function endBlock(): void {}
function getBlock(): string { return ''; }
function parentBlock(): void {}

/* ---------------------------
 * Sections (legacy 1.x/2.x functions)
 * --------------------------- */
function setSection(string $name): void {}
function endSection(): void {}
/** @return string|null */
function getSection(string $name) {}
function preSection(string $name, string $content): void {}
function addSection(string $name, string $content): void {}
function hasSection(string $name): bool { return false; }

/* ---------------------------
 * General Helpers (1.x–3.x)
 * --------------------------- */
function anchor($href, $text = null, array $attrs = []): string { return ''; }
function base($href = '', array $attrs = []): string { return ''; }
function dl(array $items, array $attrs = []): string { return ''; }
function image($src, array $attrs = []): string { return ''; }
function items(array $items, string $tag = 'ul', array $attrs = []): string { return ''; }
function link($href, $text = null, array $attrs = []): string { return ''; }
function linkStylesheet($href, array $attrs = []): string { return ''; }
function meta($nameOrAttrs, $content = null, array $attrs = []): string { return ''; } // 3.x
function ol(array $items, array $attrs = []): string { return ''; }
function script($srcOrContent, array $attrs = []): string { return ''; }
function ul(array $items, array $attrs = []): string { return ''; }
/* 1.x/2.x compatibility helpers */
function metaName($name, $content, array $attrs = []): string { return ''; }     // 1.x/2.x
function metaHttp($httpEquiv, $content, array $attrs = []): string { return ''; } // 1.x/2.x

/* ------------------------------------------------------
 * Helper wrapper for reserved-word directives like extends
 * ------------------------------------------------------ */
if (!class_exists('QiqRuntimeFunctions')) {
    /**
     * @internal IDE helper only: surfaces Qiq compiler-generated calls so PhpStorm
     * inspections can validate them.
     *
     * - `extends` is exposed because the directive collides with a PHP keyword.
     * - `h/a/j/u/c` mirror Qiq\Template::h()/a()/j()/u()/c() but as static methods,
     *   carrying the same PHPDoc constraints. Qiq compiles `{{h $x }}` into
     *   `<?= $this->h($x) ?>`, but $this is untyped inside template scope so the
     *   plugin routes escape directives through this helper to make the
     *   `null|scalar|\Stringable` signature visible to inspections.
     */
    final class QiqRuntimeFunctions
    {
        public static function extends(string $path): void {}

        // Parameters are typed via PHPDoc only; the native `mixed` keyword
        // (PHP 8.0+) would break stub parsing for projects whose Language
        // Level is set to PHP 7.x.

        /** @param null|scalar|\Stringable $raw */
        public static function h($raw): string { return ''; }

        /** @param null|scalar|\Stringable|array<null|scalar|\Stringable|array<null|scalar|\Stringable>> $raw */
        public static function a($raw): string { return ''; }

        /** @param null|scalar|\Stringable $raw */
        public static function j($raw): string { return ''; }

        /** @param null|scalar|\Stringable $raw */
        public static function u($raw): string { return ''; }

        /** @param null|scalar|\Stringable $raw */
        public static function c($raw): string { return ''; }
    }
}

if (!class_exists('QiqRuntimeFunctionsStrict')) {
    /**
     * @internal IDE helper only: mirrors Qiq 1.x Escape signatures which
     * accept `string` (and `string|array` for `a()`) only. Used by the
     * plugin when composer.lock pins qiq/qiq to ^1.0; v2.x/v3.x relax
     * the signature to `mixed` and use {@see QiqRuntimeFunctions}.
     */
    final class QiqRuntimeFunctionsStrict
    {
        public static function h(string $raw): string { return ''; }

        // a() accepts `string|array` in Qiq 1.x. The union type is PHP 8.0+
        // syntax, so it is expressed via PHPDoc to keep the stub parseable
        // under PHP 7.x language levels.

        /** @param string|array<array-key, string|array> $raw */
        public static function a($raw): string { return ''; }

        public static function j(string $raw): string { return ''; }

        public static function u(string $raw): string { return ''; }

        public static function c(string $raw): string { return ''; }
    }
}

/* ---------------------------
 * Form Helpers (3.x; keep broad for convenience)
 * --------------------------- */
function form(array $attrs = [], $content = null): string { return ''; }

/* Inputs (single) */
function inputField(string $type, $name, $value = null, array $attrs = []): string { return ''; }
function textField($name, $value = null, array $attrs = []): string { return ''; }
function passwordField($name, $value = null, array $attrs = []): string { return ''; }
function emailField($name, $value = null, array $attrs = []): string { return ''; }
function numberField($name, $value = null, array $attrs = []): string { return ''; }
function hiddenField($name, $value = null, array $attrs = []): string { return ''; }
function fileField($name, array $attrs = []): string { return ''; }
function colorField($name, $value = null, array $attrs = []): string { return ''; }
function dateField($name, $value = null, array $attrs = []): string { return ''; }
function datetimeField($name, $value = null, array $attrs = []): string { return ''; }
function datetimeLocalField($name, $value = null, array $attrs = []): string { return ''; }
function monthField($name, $value = null, array $attrs = []): string { return ''; }
function rangeField($name, $value = null, array $attrs = []): string { return ''; }
function searchField($name, $value = null, array $attrs = []): string { return ''; }
function telField($name, $value = null, array $attrs = []): string { return ''; }
function timeField($name, $value = null, array $attrs = []): string { return ''; }
function urlField($name, $value = null, array $attrs = []): string { return ''; }
function weekField($name, $value = null, array $attrs = []): string { return ''; }

/* Radios/Checkboxes */
function checkboxField($name, $value = null, array $attrs = []): string { return ''; }
function checkboxFields($name, array $choices, array $attrs = []): string { return ''; }
function radioField($name, $value = null, array $attrs = []): string { return ''; }
function radioFields($name, array $choices, array $attrs = []): string { return ''; }

/* Select & Textarea */
function select($name, array $options, $selected = null, array $attrs = []): string { return ''; }
function textarea($name, $value = null, array $attrs = []): string { return ''; }

/* Buttons & Labels */
function button($text, array $attrs = []): string { return ''; }
function submitButton($text = 'Submit', array $attrs = []): string { return ''; }
function resetButton($text = 'Reset', array $attrs = []): string { return ''; }
function imageButton($src, array $attrs = []): string { return ''; }
function label($for, $text = null, array $attrs = []): string { return ''; }

/* -------------------------------------------------
 * Minimal class for $this->...() calls in templates
 * ------------------------------------------------- */
if (!class_exists('QiqTemplate')) {
    /**
     * @internal IDE helper only: the type used for `$this` inside templates.
     *
     * Extends the real \Qiq\Template (when installed) so `$this` is-a
     * \Qiq\Template — passing `$this` to a helper typed `Qiq\Template $qiq`
     * type-checks — while re-declaring the template methods as `public` so
     * `$this->setSection(...)` etc. do not trip protected-member-access (the real
     * methods are `protected` on Qiq\TemplateCore). Without Qiq installed the
     * parent is unresolved but this class's own methods still resolve.
     */
    class QiqTemplate extends \Qiq\Template
    {
        /* Magic methods (match Qiq\TemplateCore): data access via $this->var and
         * helper dispatch via $this->helper(...), so a typed $this does not flag
         * dynamic property/helper access as undefined. */
        /** @return mixed */
        public function __get(string $name) { return null; }
        public function __isset(string $name): bool { return false; }
        /** @return mixed */
        public function __call(string $name, array $arguments) { return null; }

        /* Template control */
        public function render(string $path, ...$args): void {}
        public function setLayout(string $path): void {}
        public function extends(string $path): void {}
        public function getContent(): string { return ''; }

        /* Blocks */
        public function setBlock(string $name): void {}
        public function endBlock(): void {}
        public function getBlock(): string { return ''; }
        public function parentBlock(): void {}

        /* Sections (legacy) */
        public function setSection(string $name): void {}
        public function endSection(): void {}
        /** @return string|null */
        public function getSection(string $name) {}
        public function preSection(string $name, string $content): void {}
        public function addSection(string $name, string $content): void {}
        public function hasSection(string $name): bool { return false; }

        /* General Helpers (as methods) */
        public function anchor($href, $text = null, array $attrs = []): string { return ''; }
        public function base($href = '', array $attrs = []): string { return ''; }
        public function dl(array $items, array $attrs = []): string { return ''; }
        public function image($src, array $attrs = []): string { return ''; }
        public function items(array $items, string $tag = 'ul', array $attrs = []): string { return ''; }
        public function link($href, $text = null, array $attrs = []): string { return ''; }
        public function linkStylesheet($href, array $attrs = []): string { return ''; }
        public function meta($nameOrAttrs, $content = null, array $attrs = []): string { return ''; }
        public function ol(array $items, array $attrs = []): string { return ''; }
        public function script($srcOrContent, array $attrs = []): string { return ''; }
        public function ul(array $items, array $attrs = []): string { return ''; }
        public function metaName($name, $content, array $attrs = []): string { return ''; }
        public function metaHttp($httpEquiv, $content, array $attrs = []): string { return ''; }

        /* Form Helpers (as methods) */
        public function form(array $attrs = [], $content = null): string { return ''; }
        public function inputField(string $type, $name, $value = null, array $attrs = []): string { return ''; }
        public function textField($name, $value = null, array $attrs = []): string { return ''; }
        public function passwordField($name, $value = null, array $attrs = []): string { return ''; }
        public function emailField($name, $value = null, array $attrs = []): string { return ''; }
        public function numberField($name, $value = null, array $attrs = []): string { return ''; }
        public function hiddenField($name, $value = null, array $attrs = []): string { return ''; }
        public function fileField($name, array $attrs = []): string { return ''; }
        public function colorField($name, $value = null, array $attrs = []): string { return ''; }
        public function dateField($name, $value = null, array $attrs = []): string { return ''; }
        public function datetimeField($name, $value = null, array $attrs = []): string { return ''; }
        public function datetimeLocalField($name, $value = null, array $attrs = []): string { return ''; }
        public function monthField($name, $value = null, array $attrs = []): string { return ''; }
        public function rangeField($name, $value = null, array $attrs = []): string { return ''; }
        public function searchField($name, $value = null, array $attrs = []): string { return ''; }
        public function telField($name, $value = null, array $attrs = []): string { return ''; }
        public function timeField($name, $value = null, array $attrs = []): string { return ''; }
        public function urlField($name, $value = null, array $attrs = []): string { return ''; }
        public function weekField($name, $value = null, array $attrs = []): string { return ''; }
        public function checkboxField($name, $value = null, array $attrs = []): string { return ''; }
        public function checkboxFields($name, array $choices, array $attrs = []): string { return ''; }
        public function radioField($name, $value = null, array $attrs = []): string { return ''; }
        public function radioFields($name, array $choices, array $attrs = []): string { return ''; }
        public function select($name, array $options, $selected = null, array $attrs = []): string { return ''; }
        public function textarea($name, $value = null, array $attrs = []): string { return ''; }
        public function button($text, array $attrs = []): string { return ''; }
        public function submitButton($text = 'Submit', array $attrs = []): string { return ''; }
        public function resetButton($text = 'Reset', array $attrs = []): string { return ''; }
        public function imageButton($src, array $attrs = []): string { return ''; }
        public function label($for, $text = null, array $attrs = []): string { return ''; }
    }
}
