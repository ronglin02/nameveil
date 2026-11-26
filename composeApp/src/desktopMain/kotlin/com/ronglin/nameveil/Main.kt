package com.ronglin.nameveil

import androidx.compose.runtime.remember

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args.singleOrNull() != "--gui") {
        val exitCode = runBlocking { NameVeilCli.run(args, DesktopStorageGateway()) }
        exitProcess(exitCode)
    }
    launchDesktopGui()
}

private fun launchDesktopGui() = application {
    val state = rememberWindowState(width = 1180.dp, height = 780.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "NameVeil",
        icon = painterResource("nameveil-icon-generated.png"),
        state = state,
    ) {
        val controller = remember {
            val settings = DesktopSettings()
            AppController(DesktopStorageGateway(), initialSettings = settings.load(), saveSettings = settings::save)
        }
        NameVeilApp(controller)
    }
}
