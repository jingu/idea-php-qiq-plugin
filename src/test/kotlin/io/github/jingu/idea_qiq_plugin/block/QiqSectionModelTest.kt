package io.github.jingu.idea_qiq_plugin.block

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiqSectionModelTest {

    @Test
    fun extractsSectionAndBlockNames() {
        val text = """
            {{ setSection('header') }}<h1>x</h1>{{ endSection() }}
            {{ setBlock("content") }}body{{ endBlock() }}
        """.trimIndent()

        val defs = QiqSectionModel.definitions(text)
        assertEquals(2, defs.size)
        assertEquals("header", defs[0].name)
        assertEquals(QiqBlockType.SECTION, defs[0].type)
        assertEquals("content", defs[1].name)
        assertEquals(QiqBlockType.BLOCK, defs[1].type)
    }

    @Test
    fun nameRangePointsAtTheNameInsideQuotes() {
        val text = "{{ setSection('header') }}"
        val def = QiqSectionModel.definitions(text).single()
        assertEquals("header", text.substring(def.nameRange.startOffset, def.nameRange.endOffset))
        // The range excludes the surrounding quotes.
        assertEquals(text.indexOf("header"), def.nameRange.startOffset)
    }

    @Test
    fun ignoresReadersAndOtherDirectives() {
        // getSection/getBlock are readers, not definitions; if/foreach are unrelated.
        val text = """
            {{= ${'$'}this->getSection('header') }}
            {{ if (${'$'}x): }}a{{ endif }}
            {{= getBlock('content') }}
        """.trimIndent()
        assertTrue(QiqSectionModel.definitions(text).isEmpty())
    }

    @Test
    fun skipsEmptyOrMissingNames() {
        // Empty name is not a usable symbol; a no-arg call is not a definition.
        val text = "{{ setSection('') }}x{{ endSection() }}{{ setBlock() }}y{{ endBlock() }}"
        assertTrue(QiqSectionModel.definitions(text).isEmpty())
    }

    @Test
    fun definitionsCarryTheirBlockType() {
        val text = "{{ setSection('a') }}{{ endSection() }}{{ setBlock('b') }}{{ endBlock() }}"
        val byType = QiqSectionModel.definitions(text).associate { it.name to it.type }
        assertEquals(QiqBlockType.SECTION, byType["a"])
        assertEquals(QiqBlockType.BLOCK, byType["b"])
    }

    @Test
    fun handlesDoubleQuotedNames() {
        val text = "{{ setSection(\"main\") }}{{ endSection() }}"
        assertEquals("main", QiqSectionModel.definitions(text).single().name)
    }

    @Test
    fun extractsGetSectionAndGetBlockUsages() {
        val text = """
            {{= ${'$'}this->getSection('header') }}
            {{= getBlock('content') }}
        """.trimIndent()

        val usages = QiqSectionModel.usages(text)
        assertEquals(2, usages.size)
        assertEquals("header", usages[0].name)
        assertEquals(QiqBlockType.SECTION, usages[0].type)
        assertEquals("content", usages[1].name)
        assertEquals(QiqBlockType.BLOCK, usages[1].type)
    }

    @Test
    fun usageNameRangePointsAtTheName() {
        val text = "{{= ${'$'}this->getSection('header') }}"
        val usage = QiqSectionModel.usages(text).single()
        assertEquals("header", text.substring(usage.nameRange.startOffset, usage.nameRange.endOffset))
        assertEquals(text.indexOf("header"), usage.nameRange.startOffset)
    }

    @Test
    fun usagesIgnoreDefinitionsAndSimilarNames() {
        // setSection is a definition, not a usage; hasSection is unrelated.
        val text = "{{ setSection('a') }}{{ endSection() }}{{ if (${'$'}this->hasSection('a')): }}x{{ endif }}"
        assertTrue(QiqSectionModel.usages(text).isEmpty())
    }
}
