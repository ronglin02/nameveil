package com.ronglin.nameveil

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ronglin.nameveil.core.OperationMode
import com.ronglin.nameveil.core.OperationProgress
import com.ronglin.nameveil.core.OperationReport
import com.ronglin.nameveil.core.PlatformGateway
import com.ronglin.nameveil.core.RenameEngine
import com.ronglin.nameveil.core.RenamePlan
import com.ronglin.nameveil.core.StorageLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppController(private val platform: PlatformGateway) {
    private val engine = RenameEngine(platform, platform)

    var location: StorageLocation? by mutableStateOf(null)
        private set
    var mode: OperationMode by mutableStateOf(OperationMode.Transform)
        private set
    var password: String by mutableStateOf("")
        private set
    var includeSubdirectories: Boolean by mutableStateOf(true)
        private set
    var preserveExtension: Boolean by mutableStateOf(false)
        private set
    var showPassword: Boolean by mutableStateOf(false)
        private set
    var plan: RenamePlan? by mutableStateOf(null)
        private set
    var report: OperationReport? by mutableStateOf(null)
        private set
    var progress: OperationProgress? by mutableStateOf(null)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var infoMessage: String? by mutableStateOf(null)
        private set
    var isBusy: Boolean by mutableStateOf(false)
        private set

    fun chooseDirectory(scope: CoroutineScope) = scope.launch {
        if (isBusy) return@launch
        isBusy = true
        errorMessage = null
        try {
            platform.pickDirectory()?.let {
                location = it
                invalidatePreview()
            }
        } catch (error: Exception) {
            errorMessage = error.userMessage("无法选择目录")
        } finally {
            isBusy = false
        }
    }

    fun createPreview(scope: CoroutineScope) = scope.launch {
        val selected = location
        if (selected == null) {
            errorMessage = "请先选择要处理的目录"
            return@launch
        }
        if (isBusy) return@launch

        isBusy = true
        errorMessage = null
        report = null
        try {
            plan = withContext(Dispatchers.Default) {
                engine.createPlan(
                    location = selected,
                    mode = mode,
                    password = password,
                    includeSubdirectories = includeSubdirectories,
                    preserveExtension = preserveExtension,
                )
            }
        } catch (error: Exception) {
            plan = null
            errorMessage = error.userMessage("无法生成预览")
        } finally {
            isBusy = false
        }
    }

    fun execute(scope: CoroutineScope) = scope.launch {
        val currentPlan = plan ?: return@launch
        if (isBusy) return@launch

        isBusy = true
        errorMessage = null
        report = null
        progress = OperationProgress(0, currentPlan.directories.size, "")
        try {
            report = engine.execute(currentPlan) { progress = it }
            plan = null
        } catch (error: Exception) {
            errorMessage = error.userMessage("执行失败")
        } finally {
            progress = null
            isBusy = false
        }
    }

    fun recoverPassword(scope: CoroutineScope) = scope.launch {
        val selected = location
        if (selected == null) {
            errorMessage = "请先选择包含 NAMEVEIL 配置的目录"
            return@launch
        }
        if (isBusy) return@launch

        isBusy = true
        errorMessage = null
        infoMessage = null
        try {
            password = withContext(Dispatchers.Default) {
                engine.recoverPassword(selected, includeSubdirectories)
            }
            showPassword = true
            plan = null
            report = null
            infoMessage = "密码已从当前目录的 NAMEVEIL 配置中找回并显示。"
        } catch (error: Exception) {
            errorMessage = error.userMessage("无法找回密码")
        } finally {
            isBusy = false
        }
    }

    fun updateMode(value: OperationMode) {
        if (mode != value) {
            mode = value
            invalidatePreview()
        }
    }

    fun updatePassword(value: String) {
        password = value
        invalidatePreview()
    }

    fun updateIncludeSubdirectories(value: Boolean) {
        includeSubdirectories = value
        invalidatePreview()
    }

    fun updatePreserveExtension(value: Boolean) {
        preserveExtension = value
        invalidatePreview()
    }

    fun togglePasswordVisibility() {
        showPassword = !showPassword
    }

    fun dismissError() {
        errorMessage = null
    }

    fun dismissInfo() {
        infoMessage = null
    }

    private fun invalidatePreview() {
        plan = null
        report = null
        errorMessage = null
        infoMessage = null
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback
}
