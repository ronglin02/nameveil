package com.ronglin.nameveil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = AndroidStorageGateway(this)
        val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            storage.completePicker(uri)
        }
        storage.bindPicker { picker.launch(null) }

        setContent {
            NameVeilApp(storage)
        }
    }
}
