package com.pantham.nexus.vision

import android.graphics.Bitmap
import com.pantham.nexus.intelligence.context.NexusContextAssembler
import com.pantham.nexus.vision.barcode.NexusBarcodeEngine
import com.pantham.nexus.vision.bridge.VisionEnhancedContext
import com.pantham.nexus.vision.bridge.VisionIntelligenceBridge
import com.pantham.nexus.vision.command.NexusVisionCommandInterpreter
import com.pantham.nexus.vision.context.NexusVisualContextBuilder
import com.pantham.nexus.vision.labels.NexusImageLabelEngine
import com.pantham.nexus.vision.memory.InMemoryVisionRepository
import com.pantham.nexus.vision.memory.VisionMemoryRepository
import com.pantham.nexus.vision.model.VisionAnalysisResult
import com.pantham.nexus.vision.model.VisionFrame
import com.pantham.nexus.vision.model.VisionRequest
import com.pantham.nexus.vision.model.VisionSource
import com.pantham.nexus.vision.model.VisionTaskType
import com.pantham.nexus.vision.objects.EmptyObjectDetector
import com.pantham.nexus.vision.objects.NexusObjectDetector
import com.pantham.nexus.vision.ocr.NexusTextRecognitionEngine
import com.pantham.nexus.vision.pipeline.NexusVisionPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class NexusVisionCoreTest {

    @Test
    fun command_interpreter_maps_hinglish_screen_query_to_describe_mode() {
        val interpreter = NexusVisionCommandInterpreter()
        val instruction = interpreter.createInstruction("is screen par kya hai?")
        assertEquals(com.pantham.nexus.vision.command.VisionMode.DESCRIBE, instruction.mode)
        assertTrue(instruction.instruction.contains("visible screen"))
    }

    @Test
    fun command_interpreter_maps_error_query_to_troubleshoot_mode() {
        val interpreter = NexusVisionCommandInterpreter()
        val instruction = interpreter.createInstruction("ye error samjhao aur theek karo")
        assertEquals(com.pantham.nexus.vision.command.VisionMode.TROUBLESHOOT, instruction.mode)
    }

    @Test
    fun visual_context_builder_creates_structured_context_with_objects() {
        val builder = NexusVisualContextBuilder()
        val sampleResult = VisionAnalysisResult(
            frameId = "test-frame-1",
            source = VisionSource.SCREEN,
            taskType = VisionTaskType.SCREEN_ANALYSIS,
            processingTimeMs = 45L,
            fullText = "Settings > Display",
            objects = listOf(
                com.pantham.nexus.vision.model.VisionObject(
                    label = "Toggle Switch",
                    confidence = 0.95f
                )
            ),
            naturalDescription = "Screen showing display preferences."
        )

        val visualContext = builder.build(sampleResult)
        assertEquals("test-frame-1", visualContext.frameId)
        assertEquals("Settings > Display", visualContext.visibleText)
        assertTrue(visualContext.description?.contains("Toggle Switch") == true)
        assertTrue(visualContext.description?.contains("display preferences") == true)
    }

    @Test
    fun vision_memory_repository_bounds_history_to_ten_items() = runBlocking {
        val memory: VisionMemoryRepository = InMemoryVisionRepository()
        for (i in 1..15) {
            val ctx = com.pantham.nexus.vision.model.VisualContext(
                frameId = "frame-$i",
                source = VisionSource.CAMERA,
                description = "Frame item $i",
                visibleText = "Text $i",
                detectedObjects = emptyList(),
                detectedLabels = emptyList(),
                barcodes = emptyList(),
                timestamp = System.currentTimeMillis() + i
            )
            memory.save(ctx)
        }

        val recent = memory.getRecent(20)
        assertEquals(10, recent.size)
        assertEquals("frame-15", recent.first().frameId)
    }
}
