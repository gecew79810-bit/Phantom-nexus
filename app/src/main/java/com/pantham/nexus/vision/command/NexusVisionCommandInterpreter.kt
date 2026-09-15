package com.pantham.nexus.vision.command

import com.pantham.nexus.vision.model.VisionAnalysisResult

class NexusVisionCommandInterpreter {

    fun createInstruction(
        userInput: String
    ): VisionInstruction {

        val input =
            userInput
                .lowercase()
                .trim()

        return when {

            input.contains(
                "kya hai"
            ) ||
            input.contains(
                "what is"
            ) ->
                VisionInstruction(
                    mode =
                        VisionMode.DESCRIBE,
                    instruction =
                        "Describe the visible screen or scene clearly."
                )

            input.contains(
                "error"
            ) ||
            input.contains(
                "problem"
            ) ||
            input.contains(
                "issue"
            ) ->
                VisionInstruction(
                    mode =
                        VisionMode.TROUBLESHOOT,
                    instruction =
                        "Identify visible errors and explain them."
                )

            input.contains(
                "read"
            ) ||
            input.contains(
                "padho"
            ) ->
                VisionInstruction(
                    mode =
                        VisionMode.OCR,
                    instruction =
                        "Extract and organize visible text."
                )

            input.contains(
                "compare"
            ) ->
                VisionInstruction(
                    mode =
                        VisionMode.COMPARE,
                    instruction =
                        "Identify meaningful visual differences."
                )

            input.contains(
                "click"
            ) ||
            input.contains(
                "kahan click"
            ) ->
                VisionInstruction(
                    mode =
                        VisionMode.GUIDE,
                    instruction =
                        "Identify the relevant visible UI element and explain where it is."
                )

            else ->
                VisionInstruction(
                    mode =
                        VisionMode.GENERAL,
                    instruction =
                        userInput
                )
        }
    }
}

enum class VisionMode {
    DESCRIBE,
    TROUBLESHOOT,
    OCR,
    COMPARE,
    GUIDE,
    GENERAL
}

data class VisionInstruction(
    val mode: VisionMode,
    val instruction: String
)
