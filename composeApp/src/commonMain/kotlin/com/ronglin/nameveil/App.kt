package com.ronglin.nameveil

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronglin.nameveil.core.OperationMode
import com.ronglin.nameveil.core.OperationReport
import com.ronglin.nameveil.core.PlatformGateway
import com.ronglin.nameveil.core.RenamePlan

private val LightColors = lightColorScheme(
    primary = Color(0xFF315BE8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Color(0xFF10245F),
    secondary = Color(0xFF5B5D72),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFCFCFF),
    surfaceVariant = Color(0xFFE8EAF2),
    outline = Color(0xFFB7BBC9),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF002C72),
    primaryContainer = Color(0xFF17449D),
    onPrimaryContainer = Color(0xFFDCE2FF),
    background = Color(0xFF111318),
    surface = Color(0xFF191B21),
    surfaceVariant = Color(0xFF292B34),
    outline = Color(0xFF8E909D),
)

@Composable
fun NameVeilApp(platform: PlatformGateway) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val controller = remember(platform) { AppController(platform) }
    val scope = rememberCoroutineScope()
    var showConfirmation by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppBackground(colors) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    val wide = maxWidth >= 900.dp
                    Column(Modifier.fillMaxSize()) {
                        AppHeader()
                        Spacer(Modifier.height(18.dp))

                        if (wide) {
                            Row(
                                Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                Column(
                                    Modifier
                                        .width(380.dp)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    ControlPanel(controller, scope, onExecute = { showConfirmation = true })
                                    Spacer(Modifier.height(12.dp))
                                }
                                PreviewPanel(
                                    controller = controller,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    fillAvailableHeight = true,
                                )
                            }
                        } else {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                ControlPanel(controller, scope, onExecute = { showConfirmation = true })
                                PreviewPanel(
                                    controller = controller,
                                    modifier = Modifier.fillMaxWidth(),
                                    fillAvailableHeight = false,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showConfirmation) {
            val plan = controller.plan
            AlertDialog(
                onDismissRequest = { if (!controller.isBusy) showConfirmation = false },
                icon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                title = { Text(if (plan?.mode == OperationMode.Restore) "确认恢复文件名？" else "确认隐匿文件名？") },
                text = {
                    Text(
                        "将处理 ${plan?.mappings?.size ?: 0} 个文件、${plan?.directories?.size ?: 0} 个目录。" +
                            if (plan?.mode == OperationMode.Transform) " 每个目录都会保存独立 NAMEVEIL 配置。" else " 成功后将删除对应 NAMEVEIL 配置。",
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showConfirmation = false
                        controller.execute(scope)
                    }) { Text("确认执行") }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmation = false }) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun AppBackground(colors: ColorScheme, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(
                    colors.background,
                    colors.primaryContainer.copy(alpha = 0.28f),
                    colors.background,
                ),
            ),
        ),
    ) { content() }
}

@Composable
private fun AppHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, Color(0xFF7C4DFF)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DriveFileRenameOutline, null, tint = Color.White, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text("NameVeil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "名隐 · 离线隐匿 · 随目录恢复",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
        ) {
            Text(
                "v1.0",
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ControlPanel(
    controller: AppController,
    scope: kotlinx.coroutines.CoroutineScope,
    onExecute: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PanelCard {
            SectionTitle("处理方式", "选择隐匿名称或根据 NAMEVEIL 配置恢复")
            Spacer(Modifier.height(12.dp))
            ModeSelector(controller.mode, controller::updateMode)
        }

        PanelCard {
            SectionTitle("工作目录", "应用只会访问你明确选择的目录")
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { controller.chooseDirectory(scope) },
                enabled = !controller.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text(if (controller.location == null) "选择目录" else "更换目录")
            }
            controller.location?.let { location ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        location.displayPath,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        PanelCard {
            SectionTitle("操作选项", "预览后仍需再次确认才会修改文件")
            Spacer(Modifier.height(8.dp))
            OptionRow(
                title = "包含子目录",
                description = "递归处理所选目录下的所有层级",
                checked = controller.includeSubdirectories,
                onCheckedChange = controller::updateIncludeSubdirectories,
            )
            if (controller.mode == OperationMode.Transform) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                OptionRow(
                    title = "保留扩展名（不推荐）",
                    description = "关闭后才能规避仅根据 .jpg、.pdf 等后缀工作的扫描器",
                    checked = controller.preserveExtension,
                    onCheckedChange = controller::updatePreserveExtension,
                )
            }
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(
                value = controller.password,
                onValueChange = controller::updatePassword,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("配置密码") },
                leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                visualTransformation = if (controller.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = controller::togglePasswordVisibility) {
                        Icon(
                            if (controller.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (controller.showPassword) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
            Text(
                if (controller.mode == OperationMode.Transform) "映射使用 AES-256-GCM 包装；密码会以君子模式保存在当前目录，忘记后可找回。" else "只识别固定的 NAMEVEIL 配置格式。忘记密码可直接从配置找回。",
                Modifier.padding(top = 7.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (controller.mode == OperationMode.Restore) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { controller.recoverPassword(scope) },
                    enabled = !controller.isBusy && controller.location != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Key, null)
                    Spacer(Modifier.width(8.dp))
                    Text("从配置找回密码")
                }
            }
        }

        controller.errorMessage?.let { message ->
            MessageCard(
                icon = Icons.Outlined.ErrorOutline,
                title = "需要处理",
                message = message,
                error = true,
                onDismiss = controller::dismissError,
            )
        }

        controller.infoMessage?.let { message ->
            MessageCard(
                icon = Icons.Outlined.CheckCircle,
                title = "密码已找回",
                message = message,
                error = false,
                onDismiss = controller::dismissInfo,
            )
        }

        controller.report?.let { ReportCard(it) }

        controller.progress?.let { progress ->
            PanelCard {
                Text("正在处理 ${progress.completedDirectories}/${progress.totalDirectories}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (progress.totalDirectories == 0) 0f else progress.completedDirectories.toFloat() / progress.totalDirectories },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (progress.currentDirectory.isNotEmpty()) {
                    Text(
                        progress.currentDirectory,
                        Modifier.padding(top = 7.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Button(
            onClick = { controller.createPreview(scope) },
            enabled = !controller.isBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (controller.isBusy && controller.progress == null) {
                CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.AutoMirrored.Rounded.ManageSearch, null)
            }
            Spacer(Modifier.width(8.dp))
            Text("生成改名预览")
        }

        if (controller.plan != null) {
            FilledTonalButton(
                onClick = onExecute,
                enabled = !controller.isBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Icon(Icons.Rounded.DriveFileRenameOutline, null)
                Spacer(Modifier.width(8.dp))
                Text(if (controller.mode == OperationMode.Transform) "执行文件名隐匿" else "执行文件名恢复")
            }
        }
    }
}

@Composable
private fun PreviewPanel(
    controller: AppController,
    modifier: Modifier,
    fillAvailableHeight: Boolean,
) {
    Card(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("改名预览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "执行前核对每一个原文件名与目标文件名",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                controller.plan?.let { plan ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "${plan.mappings.size} 个文件",
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            val plan = controller.plan
            if (plan == null) {
                EmptyPreview(controller.report != null)
            } else {
                PreviewHeader(plan)
                Spacer(Modifier.height(10.dp))
                val scrollModifier = if (fillAvailableHeight) {
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }
                Column(scrollModifier.fillMaxWidth()) {
                    val visibleMappings = if (fillAvailableHeight) plan.mappings else plan.mappings.take(40)
                    visibleMappings.forEachIndexed { index, mapping ->
                        MappingRow(
                            index = index + 1,
                            directory = mapping.directoryPath,
                            from = mapping.fromName,
                            to = mapping.toName,
                        )
                        if (index != visibleMappings.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        }
                    }
                    if (!fillAvailableHeight && plan.mappings.size > visibleMappings.size) {
                        Text(
                            "移动端仅显示前 ${visibleMappings.size} 条；全部 ${plan.mappings.size} 条都会按此配置处理。",
                            Modifier.padding(top = 14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(plan: RenamePlan) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "已检查 ${plan.directories.size} 个目录，没有发现名称冲突",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MappingRow(index: Int, directory: String, from: String, to: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(29.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(index.toString(), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(directory, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(from, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text("→  $to", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyPreview(operationFinished: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 70.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(82.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (operationFinished) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Rounded.ManageSearch,
                null,
                Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (operationFinished) "本次操作已经结束" else "还没有生成预览",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (operationFinished) "如需继续，请重新选择设置并生成预览。" else "选择目录、设置密码，然后点击“生成改名预览”。",
            Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ModeSelector(selected: OperationMode, onSelected: (OperationMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        ModeButton("隐匿文件名", OperationMode.Transform, selected, onSelected, Modifier.weight(1f))
        ModeButton("恢复文件名", OperationMode.Restore, selected, onSelected, Modifier.weight(1f))
    }
}

@Composable
private fun ModeButton(
    text: String,
    mode: OperationMode,
    selected: OperationMode,
    onSelected: (OperationMode) -> Unit,
    modifier: Modifier,
) {
    val active = mode == selected
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelected(mode) },
        color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (active) 2.dp else 0.dp,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text,
            Modifier.padding(vertical = 10.dp),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun OptionRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun MessageCard(
    icon: ImageVector,
    title: String,
    message: String,
    error: Boolean,
    onDismiss: (() -> Unit)? = null,
) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = color, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
            }
            onDismiss?.let {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Text("×", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun ReportCard(report: OperationReport) {
    val message = buildString {
        append("已完成 ${report.completedDirectories} 个目录，共改名 ${report.renamedFiles} 个文件。")
        if (report.failures.isNotEmpty()) append("\n失败：${report.failures.joinToString("\n")}")
        if (report.warnings.isNotEmpty()) append("\n提示：${report.warnings.joinToString("\n")}")
    }
    MessageCard(
        icon = if (report.isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
        title = if (report.isSuccess) "操作完成" else "操作部分完成",
        message = message,
        error = !report.isSuccess,
    )
}
