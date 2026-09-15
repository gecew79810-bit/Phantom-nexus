package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.CommandParserUtility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandParserUtilityTest {

    private lateinit var context: Context
    private lateinit var parser: CommandParserUtility

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        parser = CommandParserUtility(context)
    }

    @Test
    fun testParseWifiSettingsEnglishAndHindi() {
        val resultEn = parser.parse("Open Wi-Fi settings please")
        assertTrue(resultEn is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmdEn = resultEn as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_WIFI_SETTINGS, cmdEn.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.WIFI, cmdEn.category)

        val resultHi = parser.parse("वाईफाई सेटिंग खोलो")
        assertTrue(resultHi is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmdHi = resultHi as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_WIFI_SETTINGS, cmdHi.intentAction)
    }

    @Test
    fun testParseBluetoothSettings() {
        val result = parser.parse("bluetooth setting open karo")
        assertTrue(result is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, cmd.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.BLUETOOTH, cmd.category)
    }

    @Test
    fun testParseDisplayAndBrightnessSettings() {
        val result = parser.parse("स्क्रीन ब्राइटनेस सेटिंग दिखाओ")
        assertTrue(result is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, cmd.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.DISPLAY, cmd.category)
    }

    @Test
    fun testParseSoundSettings() {
        val result = parser.parse("आवाज की सेटिंग बदलो")
        assertTrue(result is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_SOUND_SETTINGS, cmd.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.SOUND, cmd.category)
    }

    @Test
    fun testParseBatterySaverSettings() {
        val result = parser.parse("open battery saver settings")
        assertTrue(result is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_BATTERY_SAVER_SETTINGS, cmd.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.BATTERY, cmd.category)
    }

    @Test
    fun testParseAccessibilitySettings() {
        val result = parser.parse("एक्सेसिबिलिटी सेटिंग्स खोलो")
        assertTrue(result is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, cmd.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.ACCESSIBILITY, cmd.category)
    }

    @Test
    fun testParseGeneralSettings() {
        val result = parser.parse("phone settings kholo")
        assertTrue(result is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_SETTINGS, cmd.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.GENERAL_SETTINGS, cmd.category)
    }

    @Test
    fun testParseImageGenerationGeneric() {
        val result = parser.parse("इमेज बनाओ एक उड़ती हुई कार की")
        assertTrue(result is CommandParserUtility.ParsedCommand.ImageGenerationCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.ImageGenerationCommand
        assertNotNull(cmd.prompt)
        assertTrue(cmd.prompt.isNotEmpty())
        assertTrue(cmd.masterArtPrompt.contains("8K photorealistic"))
        assertTrue(cmd.deepLinkUri.toString().contains("chatgpt.com"))
    }

    @Test
    fun testParseImageGenerationWithGemini() {
        val result = parser.parse("Gemini se create image of futuristic neon cyberpunk city")
        assertTrue(result is CommandParserUtility.ParsedCommand.ImageGenerationCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.ImageGenerationCommand
        assertEquals(CommandParserUtility.ImageGenPlatform.GEMINI, cmd.platform)
        assertTrue(cmd.deepLinkUri.toString().contains("gemini.google.com"))
    }

    @Test
    fun testParseImageGenerationWithBing() {
        val result = parser.parse("Bing image creator se photo banao golden temple in space")
        assertTrue(result is CommandParserUtility.ParsedCommand.ImageGenerationCommand)
        val cmd = result as CommandParserUtility.ParsedCommand.ImageGenerationCommand
        assertEquals(CommandParserUtility.ImageGenPlatform.BING_IMAGE_CREATOR, cmd.platform)
        assertTrue(cmd.deepLinkUri.toString().contains("bing.com/create"))
    }

    @Test
    fun testParseFixSettingsNaturalLanguage() {
        val resultEn = parser.parse("fix settings")
        assertTrue(resultEn is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmdEn = resultEn as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_SETTINGS, cmdEn.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.GENERAL_SETTINGS, cmdEn.category)
        assertTrue(cmdEn.suppressVoiceOutput)
        assertTrue(cmdEn.isTroubleshootingFix)
        assertTrue(cmdEn.directStatusFeedback.contains("STATUS") || cmdEn.directStatusFeedback.contains("Settings"))

        val resultHi = parser.parse("सेटिंग ठीक करो")
        assertTrue(resultHi is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmdHi = resultHi as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_SETTINGS, cmdHi.intentAction)
        assertTrue(cmdHi.suppressVoiceOutput)
    }

    @Test
    fun testParseChangeBrightnessNaturalLanguage() {
        val resultEn = parser.parse("change brightness")
        assertTrue(resultEn is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmdEn = resultEn as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, cmdEn.intentAction)
        assertEquals(CommandParserUtility.SettingCategory.DISPLAY, cmdEn.category)
        assertTrue(cmdEn.suppressVoiceOutput)
        assertTrue(cmdEn.directStatusFeedback.contains("Brightness"))

        val resultAdjust = parser.parse("adjust brightness")
        assertTrue(resultAdjust is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmdAdjust = resultAdjust as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, cmdAdjust.intentAction)

        val resultHi = parser.parse("ब्राइटनेस बदलो")
        assertTrue(resultHi is CommandParserUtility.ParsedCommand.SettingsIntentCommand)
        val cmdHi = resultHi as CommandParserUtility.ParsedCommand.SettingsIntentCommand
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, cmdHi.intentAction)
        assertTrue(cmdHi.suppressVoiceOutput)
    }

    @Test
    fun testExecuteSettingsIntentSafe() {
        val result = parser.parse("open wifi settings")
        val execResult = parser.execute(result)
        assertTrue(execResult.isSuccess)
        assertEquals("Wi-Fi Settings", execResult.actionTitle)
        assertTrue(execResult.suppressVoiceOutput)
        assertTrue(execResult.directStatusFeedback.isNotEmpty())
    }
}
