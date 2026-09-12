package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ActionExecutor
import com.example.util.AppType
import com.example.util.ParsedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Jarvis", appName)
  }

  @Test
  fun `parse open youtube commands`() {
    val action1 = ActionExecutor.parseCommand("Open YouTube")
    assertTrue(action1 is ParsedAction.OpenYouTube)

    val action2 = ActionExecutor.parseCommand("YouTube kholo")
    assertTrue(action2 is ParsedAction.OpenYouTube)

    val action3 = ActionExecutor.parseCommand("यूट्यूब खोलो")
    assertTrue(action3 is ParsedAction.OpenYouTube)
  }

  @Test
  fun `parse play music on youtube commands`() {
    val action1 = ActionExecutor.parseCommand("Play Believer on YouTube")
    assertTrue(action1 is ParsedAction.PlayYouTube)
    assertEquals("Believer", (action1 as ParsedAction.PlayYouTube).songQuery)

    val action2 = ActionExecutor.parseCommand("YouTube pe Kesariya chalao")
    assertTrue(action2 is ParsedAction.PlayYouTube)
    assertEquals("Kesariya", (action2 as ParsedAction.PlayYouTube).songQuery)

    val action3 = ActionExecutor.parseCommand("play despacito on youtube")
    assertTrue(action3 is ParsedAction.PlayYouTube)
    assertEquals("despacito", (action3 as ParsedAction.PlayYouTube).songQuery)
  }

  @Test
  fun `parse open apps and call commands`() {
    val whatsappAction = ActionExecutor.parseCommand("Open WhatsApp")
    assertTrue(whatsappAction is ParsedAction.OpenApp)
    assertEquals(AppType.WHATSAPP, (whatsappAction as ParsedAction.OpenApp).appType)

    val cameraAction = ActionExecutor.parseCommand("Open Camera")
    assertTrue(cameraAction is ParsedAction.OpenApp)
    assertEquals(AppType.CAMERA, (cameraAction as ParsedAction.OpenApp).appType)

    val dialerAction = ActionExecutor.parseCommand("Open Dialer")
    assertTrue(dialerAction is ParsedAction.OpenApp)
    assertEquals(AppType.DIALER, (dialerAction as ParsedAction.OpenApp).appType)

    val settingsAction = ActionExecutor.parseCommand("Open Settings")
    assertTrue(settingsAction is ParsedAction.OpenApp)
    assertEquals(AppType.SETTINGS, (settingsAction as ParsedAction.OpenApp).appType)

    val callAction = ActionExecutor.parseCommand("Call Mom")
    assertTrue(callAction is ParsedAction.MakeCall)
    assertEquals("Mom", (callAction as ParsedAction.MakeCall).target)
  }

  @Test
  fun `casual talk or general questions do not launch apps`() {
    val chat1 = ActionExecutor.parseCommand("What is the weather today?")
    assertTrue(chat1 is ParsedAction.Chat)

    val chat2 = ActionExecutor.parseCommand("Who is the prime minister?")
    assertTrue(chat2 is ParsedAction.Chat)

    val chat3 = ActionExecutor.parseCommand("Tell me a funny joke")
    assertTrue(chat3 is ParsedAction.Chat)

    val chat4 = ActionExecutor.parseCommand("Namaste Jarvis kaise ho")
    assertTrue(chat4 is ParsedAction.Chat)
  }
}
