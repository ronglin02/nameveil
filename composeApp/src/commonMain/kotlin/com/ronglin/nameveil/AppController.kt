package com.ronglin.nameveil

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ronglin.nameveil.core.OperationMode
import com.ronglin.nameveil.core.DEFAULT_WORKERS
import com.ronglin.nameveil.core.MAX_WORKERS
import com.ronglin.nameveil.core.automaticWorkers
import com.ronglin.nameveil.core.OperationProgress
import com.ronglin.nameveil.core.OperationReport
import com.ronglin.nameveil.core.PlatformGateway
import com.ronglin.nameveil.core.RenameEngine
import com.ronglin.nameveil.core.RenamePlan
import com.ronglin.nameveil.core.StorageLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThreadMode(val label: String) { Auto("自动"), On("是"), Off("否") }

@kotlinx.serialization.Serializable
data class RunSettings(
    val threadMode: ThreadMode = ThreadMode.Auto,
    val manualWorkers: Int = DEFAULT_WORKERS,
    val includeSubdirectories: Boolean = true,
    val preserveExtension: Boolean = false,
)

class AppController(
    private val platform: PlatformGateway,
    initialSettings: RunSettings = RunSettings(),
    private val saveSettings: (RunSettings) -> Unit = {},
) {
    var settings by mutableStateOf(initialSettings)
        private set
    var workerInput by mutableStateOf(initialSettings.manualWorkers.toString())
        private set
    val autoWorkers = automaticWorkers()
    val validWorkers get() = workerInput.toIntOrNull() in 1..MAX_WORKERS

    fun updateSettings(value: RunSettings) {
        if (isBusy) return
        if (settings.includeSubdirectories != value.includeSubdirectories || settings.preserveExtension != value.preserveExtension) {
            invalidatePreview()
        }
        settings = value
        try {
            saveSettings(value)
        } catch (error: Exception) {
            errorMessage = "设置已在本次运行生效，保存失败：${error.message.orEmpty()}"
        }
    }

    fun updateWorkerInput(text: String) {
        if (isBusy) return
        workerInput = text
        text.toIntOrNull()?.takeIf { it in 1..MAX_WORKERS }?.let {
            updateSettings(settings.copy(manualWorkers = it))
        }
    }

    private fun engineForSettings(): RenameEngine? {
        val workers = when (settings.threadMode) {
            ThreadMode.Auto -> autoWorkers
            ThreadMode.Off -> 1
            ThreadMode.On -> workerInput.toIntOrNull()?.takeIf { it in 1..MAX_WORKERS }
        }
        if (workers == null) {
            errorMessage = "请在设置中输入 1 至 $MAX_WORKERS 的并发数。"
            return null
        }
        return RenameEngine(platform, platform, workers)
    }

    var location: StorageLocation? by mutableStateOf(null)
        private set
    var mode: OperationMode by mutableStateOf(OperationMode.Restore)
        private set
    var password: String by mutableStateOf("")
        private set
    val includeSubdirectories get() = settings.includeSubdirectories
    val preserveExtension get() = settings.preserveExtension
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

        val engine = engineForSettings() ?: return@launch
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

    fun prepareExecution(): Boolean {
        val currentPlan = plan ?: return false
        if (isBusy) return false
        if (settings.threadMode == ThreadMode.On && !validWorkers) {
            errorMessage = "请在设置中输入 1 至 $MAX_WORKERS 的并发数。"
            return false
        }

        isBusy = true
        errorMessage = null
        report = null
        progress = OperationProgress(0, currentPlan.directories.size, "", 0, currentPlan.mappings.size)
        return true
    }

    suspend fun executePrepared() {
        val currentPlan = checkNotNull(plan)
        val engine = checkNotNull(engineForSettings())
        try {
            report = coroutineScope {
                val updates = Channel<OperationProgress>(Channel.CONFLATED)
                launch {
                    for (update in updates) progress = update
                }
                try {
                    withContext(Dispatchers.Default) {
                        engine.execute(currentPlan) { updates.trySend(it) }
                    }
                } finally {
                    updates.close()
                }
            }
            plan = null
        } catch (cancelled: CancellationException) {
            plan = null
            errorMessage = "执行已中断，请重新生成预览以检查目录状态。"
            throw cancelled
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
                RenameEngine(platform, platform).recoverPassword(selected, includeSubdirectories)
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
        updateSettings(settings.copy(includeSubdirectories = value))
    }

    fun updatePreserveExtension(value: Boolean) {
        updateSettings(settings.copy(preserveExtension = value))
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
