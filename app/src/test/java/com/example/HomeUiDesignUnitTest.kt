package com.example

import com.example.ai.CapabilityNode
import com.example.voice.AssistantLanguage
import com.example.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HomeUiDesignUnitTest {

    @Test
    fun testCapabilityNodesEnum() {
        val nodes = CapabilityNode.values()
        assertEquals(7, nodes.size)
        assertNotNull(CapabilityNode.valueOf("THINK"))
        assertNotNull(CapabilityNode.valueOf("EXECUTE"))
        assertNotNull(CapabilityNode.valueOf("SEARCH"))
        assertNotNull(CapabilityNode.valueOf("AUTOMATE"))
        assertNotNull(CapabilityNode.valueOf("ANALYZE"))
        assertNotNull(CapabilityNode.valueOf("LEARN"))
    }

    @Test
    fun testVoiceStates() {
        val states = VoiceState.values()
        assertNotNull(VoiceState.valueOf("IDLE"))
        assertNotNull(VoiceState.valueOf("LISTENING"))
        assertNotNull(VoiceState.valueOf("THINKING"))
        assertNotNull(VoiceState.valueOf("SPEAKING"))
    }

    @Test
    fun testLanguageDefaults() {
        val lang = AssistantLanguage.ENGLISH
        assertEquals("English (India)", lang.displayName)
    }
}
