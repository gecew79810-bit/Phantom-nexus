package com.pantham.nexus.intelligence

import com.pantham.nexus.intelligence.model.IntentType
import com.pantham.nexus.intelligence.intent.IntentPattern
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals

class NexusIntelligenceTests {

    @Test
    fun compound_research_request_is_detected() =
        runTest {

            val patterns =
                IntentPattern.defaults()

            val matched =
                patterns.any { pattern ->

                    pattern.matches(
                        "research laptops compare them and make pdf"
                    ) > 0.5
                }

            assertEquals(
                true,
                matched
            )
        }

    @Test
    fun open_app_language_variant_is_supported() =
        runTest {

            val pattern =
                IntentPattern.defaults()
                    .first {
                        it.intent ==
                            IntentType.OPEN_APP
                    }

            assertEquals(
                true,
                pattern.matches(
                    "whatsapp kholo"
                ) > 0.5
            )
        }

    @Test
    fun send_message_pattern_is_detected() =
        runTest {

            val pattern =
                IntentPattern.defaults()
                    .first {
                        it.intent ==
                            IntentType.SEND_MESSAGE
                    }

            assertEquals(
                true,
                pattern.matches(
                    "rahul ko whatsapp pe message bhejo"
                ) > 0.5
            )
        }
}
