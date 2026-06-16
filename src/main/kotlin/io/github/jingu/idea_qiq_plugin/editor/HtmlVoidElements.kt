package io.github.jingu.idea_qiq_plugin.editor

/**
 * HTML void elements: tags that never take a closing tag. Shared by the formatter
 * ([QiqReindent], for HTML-tag depth) and the Enter handler ([QiqEnterHandler], for
 * Enter-between-tags) so the two never drift on what counts as a nestable element.
 */
internal val HTML_VOID_ELEMENTS: Set<String> = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr",
)
