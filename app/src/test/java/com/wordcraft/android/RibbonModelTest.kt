package com.wordcraft.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the pure logic behind the ribbon and the parameter sheets (no device needed). */
class RibbonModelTest {
    @Test
    fun parsesRequiredAndOptionalFields() {
        val fields = RibbonModel.fields("""{"size": number, "name"?: string}""")
        assertEquals(2, fields.size)
        assertEquals("size", fields[0].name)
        assertFalse(fields[0].optional)
        assertTrue(fields[1].optional)
    }

    @Test
    fun readsEnumeratedChoices() {
        assertEquals(listOf("normal", "narrow", "wide"), RibbonModel.options("\"normal|narrow|wide\""))
        assertNull(RibbonModel.options("string"))
    }

    @Test
    fun simpleTogglesRunWithoutAsking() {
        assertFalse(RibbonModel.needsInput("{}"))
        assertFalse(RibbonModel.needsInput("""{"off"?: bool}"""))
        assertTrue(RibbonModel.needsInput("""{"width": pt}"""))
        assertTrue(RibbonModel.needsInput("""{"preset"?: "normal|narrow"}"""))
    }

    @Test
    fun matchesStyleNamesLoosely() {
        assertTrue(styleMatches("Heading 1", "Heading1"))
        assertTrue(styleMatches("heading1", "Heading1"))
        assertFalse(styleMatches("", "Normal"))
        assertFalse(styleMatches("Title", "Subtitle"))
    }
}
