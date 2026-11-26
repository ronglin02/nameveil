package com.ronglin.nameveil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.ronglin.nameveil.core.OperationMode

class MainActivity : ComponentActivity() {
    private val app get() = application as NameVeilApplication
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        app.storage.completePicker(uri)
    }
    private val openPicker: () -> Unit = { picker.launch(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !app.controller.isBusy) {
            app.controller.updateMode(OperationMode.Restore)
        }
        app.storage.bindPicker(openPicker)
        setContent { NameVeilApp(app.controller, app.executionScope) }
    }

    override fun onDestroy() {
        app.storage.unbindPicker(openPicker)
        super.onDestroy()
    }
}
