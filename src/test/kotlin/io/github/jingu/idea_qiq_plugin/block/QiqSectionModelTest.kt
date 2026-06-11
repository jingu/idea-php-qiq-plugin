package io.github.jingu.idea_qiq_plugin.block

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiqSectionModelTest {

    @Test
    fun extractsSectionDefinitions() {
        val text = """
            {{ setSection('header') }}<h1>x</h1>{{ endSection() }}
            {{ setSection("body") }}body{{ endSection() }}
        """.trimIndent()

        val defs = QiqSectionModel.definitions(text)
        assertEquals(listOf("header", "body"), defs.map { it.name })
    }

    @Test
    fun recognisesAppendAndPrependSectionAsDefinitions() {
        // A section is also defined by appendSection/prependSection, not just
        // setSection (#50); each definition records the head as written.
        val text = """
            {{ setSection('a') }}x{{ endSection() }}
            {{ appendSection('b') }}y{{ endSection() }}
            {{ ${'$'}this->prependSection('c') }}z{{ endSection() }}
        """.trimIndent()

        val defs = QiqSectionModel.definitions(text)
        assertEquals(listOf("a", "b", "c"), defs.map { it.name })
        assertEquals(listOf("setSection", "appendSection", "prependSection"), defs.map { it.head })
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
    fun definitionsIgnoreReadersBlocksAndOtherDirectives() {
        // getSection/hasSection are readers; setBlock is a block (not a section);
        // if/foreach are unrelated.
        val text = """
            {{= ${'$'}this->getSection('header') }}
            {{ if (${'$'}this->hasSection('header')): }}a{{ endif }}
            {{ setBlock('content') }}x{{ endBlock() }}
        """.trimIndent()
        assertTrue(QiqSectionModel.definitions(text).isEmpty())
    }

    @Test
    fun skipsEmptyOrMissingNames() {
        // Empty name is not a usable symbol; a no-arg call is not a definition.
        val text = "{{ setSection('') }}x{{ endSection() }}{{ setSection() }}y{{ endSection() }}"
        assertTrue(QiqSectionModel.definitions(text).isEmpty())
    }

    @Test
    fun handlesDoubleQuotedNames() {
        val text = "{{ setSection(\"main\") }}{{ endSection() }}"
        assertEquals("main", QiqSectionModel.definitions(text).single().name)
    }

    @Test
    fun definitionRequiresAPlainStringFirstArgument() {
        // A non-literal or non-first-argument quoted string must not be indexed as
        // a section name (only the first argument, when a plain string, counts).
        assertTrue(QiqSectionModel.definitions("{{ setSection(${'$'}name) }}x{{ endSection() }}").isEmpty())
        assertTrue(QiqSectionModel.definitions("{{ setSection(${'$'}name, 'fallback') }}x{{ endSection() }}").isEmpty())
        assertTrue(QiqSectionModel.definitions("{{ setSection(${'$'}c ? 'a' : 'b') }}x{{ endSection() }}").isEmpty())
        // The plain-string first argument is still indexed.
        assertEquals("ok", QiqSectionModel.definitions("{{ setSection('ok') }}x{{ endSection() }}").single().name)
    }

    @Test
    fun extractsGetSectionAndHasSectionUsagesWithHead() {
        val text = """
            {{= ${'$'}this->getSection('header') }}
            {{ if (${'$'}this->hasSection('footer')): }}x{{ endif }}
        """.trimIndent()

        val usages = QiqSectionModel.usages(text)
        assertEquals(2, usages.size)
        assertEquals("header", usages[0].name)
        assertEquals("getSection", usages[0].head)
        assertEquals("footer", usages[1].name)
        assertEquals("hasSection", usages[1].head)
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
        // setSection is a definition, not a usage; getBlock has no name argument.
        val text = "{{ setSection('a') }}{{ endSection() }}{{= getBlock() }}"
        assertTrue(QiqSectionModel.usages(text).isEmpty())
    }

    @Test
    fun usagesAcceptOnlyBareOrThisReceivers() {
        // $obj->getSection(...) is some other object's method, not a Qiq section
        // read, matching the PSI gate in QiqSectionCall.
        assertTrue(QiqSectionModel.usages("{{= ${'$'}obj->getSection('x') }}").isEmpty())
        assertTrue(QiqSectionModel.usages("{{= ${'$'}page->view->getSection('x') }}").isEmpty())
        // Bare and $this-> reads are indexed.
        assertEquals("a", QiqSectionModel.usages("{{= getSection('a') }}").single().name)
        assertEquals("b", QiqSectionModel.usages("{{= ${'$'}this->getSection('b') }}").single().name)
    }
}
