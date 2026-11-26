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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronglin.nameveil.core.OperationMode
import com.ronglin.nameveil.core.MAX_WORKERS
import com.ronglin.nameveil.core.OperationReport
import kotlinx.coroutines.launch
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
fun NameVeilApp(controller: AppController, executionScope: kotlinx.coroutines.CoroutineScope? = null) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
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
                        Spacer(Modifier.height(if (wide) 12.dp else 18.dp))

                        if (wide) {
                            Row(
                                Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                Column(
                                    Modifier
                                        .width(380.dp)
                                        .fillMaxHeight(),
                                ) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        ControlPanel(controller, scope, compact = true)
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    ControlActions(
                                        controller = controller,
                                        scope = scope,
                                        onExecute = { showConfirmation = true },
                                        compact = true,
                                    )
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
                                ControlPanel(controller, scope, compact = false)
                                ControlActions(
                                    controller = controller,
                                    scope = scope,
                                    onExecute = { showConfirmation = true },
                                    compact = false,
                                )
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
                        if (controller.prepareExecution()) {
                            (executionScope ?: scope).launch { controller.executePrepared() }
                        }
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
                "v$APP_VERSION",
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
    compact: Boolean,
) {
    val sectionGap = if (compact) 8.dp else 12.dp
    val cardGap = if (compact) 10.dp else 14.dp

    Column(verticalArrangement = Arrangement.spacedBy(cardGap)) {
        PanelCard(compact) {
            SectionTitle("处理方式", "选择隐匿名称或根据 NAMEVEIL 配置恢复")
            Spacer(Modifier.height(sectionGap))
            ModeSelector(controller.mode, controller::updateMode, compact, enabled = !controller.isBusy)
        }

        PanelCard(compact) {
            SectionTitle("工作目录", "应用只会访问你明确选择的目录")
            Spacer(Modifier.height(sectionGap))
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
                Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(if (compact) 8.dp else 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = if (compact) Modifier.size(20.dp) else Modifier,
                    )
                    Spacer(Modifier.width(if (compact) 7.dp else 9.dp))
                    Text(
                        location.displayPath,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        PanelCard(compact) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            val summary = buildString {
                append(if (controller.includeSubdirectories) "包含子目录" else "仅当前目录")
                if (controller.mode == OperationMode.Transform) {
                    append(if (controller.preserveExtension) " · 保留扩展名" else " · 移除扩展名")
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) { SectionTitle("设置", summary) }
                Icon(if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    if (expanded) "收起设置" else "展开设置")
            }
            if (expanded) {
                OptionRow(
                    title = "包含子目录",
                    description = "递归处理所选目录下的所有层级",
                    checked = controller.includeSubdirectories,
                    onCheckedChange = controller::updateIncludeSubdirectories,
                    compact = compact,
                    enabled = !controller.isBusy,
                )
                if (controller.mode == OperationMode.Transform) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    OptionRow(
                        title = "保留扩展名（不推荐）",
                        description = "关闭后才能规避仅根据 .jpg、.pdf 等后缀工作的扫描器",
                        checked = controller.preserveExtension,
                        onCheckedChange = controller::updatePreserveExtension,
                        compact = compact,
                        enabled = !controller.isBusy,
                    )
                }
                ThreadSettings(controller)
            }
        }
        PanelCard(compact) {
            OutlinedTextField(
                value = controller.password,
                onValueChange = controller::updatePassword,
                enabled = !controller.isBusy,
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
                if (compact) {
                    if (controller.mode == OperationMode.Transform) {
                        "密码保存在 NAMEVEIL 配置中，忘记后可找回。"
                    } else {
                        "只识别 NAMEVEIL 配置；忘记密码可直接找回。"
                    }
                } else if (controller.mode == OperationMode.Transform) {
                    "映射使用 AES-256-GCM 包装；密码会以君子模式保存在当前目录，忘记后可找回。"
                } else {
                    "只识别固定的 NAMEVEIL 配置格式。忘记密码可直接从配置找回。"
                },
                Modifier.padding(top = if (compact) 5.dp else 7.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (controller.mode == OperationMode.Restore) {
                Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
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
            PanelCard(compact) {
                Text("已改名 ${progress.completedFiles} / ${progress.totalFiles} 个文件", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
                LinearProgressIndicator(
                    progress = { progress.completedFiles.toFloat() / progress.totalFiles.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (progress.currentDirectory.isNotEmpty()) {
                    Text(
                        progress.currentDirectory,
                        Modifier.padding(top = if (compact) 5.dp else 7.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

    }
}

@Composable
private fun ControlActions(
    controller: AppController,
    scope: kotlinx.coroutines.CoroutineScope,
    onExecute: () -> Unit,
    compact: Boolean,
) {
    val buttonHeight = if (compact) 44.dp else 48.dp
    val plan = controller.plan

    if (plan == null) {
        Button(
            onClick = { controller.createPreview(scope) },
            enabled = !controller.isBusy,
            modifier = Modifier.fillMaxWidth().height(buttonHeight),
        ) {
            if (controller.isBusy && controller.progress == null) {
                CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.AutoMirrored.Rounded.ManageSearch, null)
            }
            Spacer(Modifier.width(8.dp))
            Text("生成改名预览")
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { controller.createPreview(scope) },
                enabled = !controller.isBusy,
                modifier = Modifier.weight(1f).height(buttonHeight),
            ) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text("重新预览")
            }
            FilledTonalButton(
                onClick = onExecute,
                enabled = !controller.isBusy,
                modifier = Modifier.weight(1f).height(buttonHeight),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Icon(Icons.Rounded.DriveFileRenameOutline, null, Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (plan.mode == OperationMode.Transform) "执行隐匿" else "执行恢复")
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
                var expandedDirectories by remember(plan) { mutableStateOf(emptySet<String>()) }
                PreviewHeader(
                    plan = plan,
                    canCollapse = expandedDirectories.isNotEmpty(),
                    onCollapseAll = { expandedDirectories = emptySet() },
                )
                Spacer(Modifier.height(10.dp))
                val listModifier = if (fillAvailableHeight) {
                    Modifier.weight(1f)
                } else {
                    Modifier.height(420.dp)
                }
                LazyColumn(listModifier.fillMaxWidth()) {
                    plan.directories.forEach { directory ->
                        val expanded = directory.directoryId in expandedDirectories
                        item(key = "directory:${directory.directoryId}") {
                            DirectoryRow(
                                path = directory.relativePath,
                                fileCount = directory.mappings.size,
                                expanded = expanded,
                                onClick = {
                                    expandedDirectories = if (expanded) {
                                        expandedDirectories - directory.directoryId
                                    } else {
                                        expandedDirectories + directory.directoryId
                                    }
                                },
                            )
                        }
                        if (expanded) {
                            itemsIndexed(
                                items = directory.mappings,
                                key = { _, mapping -> "mapping:${directory.directoryId}:${mapping.itemId}" },
                            ) { index, mapping ->
                                MappingRow(
                                    index = index + 1,
                                    from = mapping.fromName,
                                    to = mapping.toName,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(
    plan: RenamePlan,
    canCollapse: Boolean,
    onCollapseAll: () -> Unit,
) {
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
            "${plan.directories.size} 个目录 · ${plan.mappings.size} 个文件，没有名称冲突",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        TextButton(
            onClick = onCollapseAll,
            enabled = canCollapse,
        ) {
            Text("全部收起")
        }
    }
}

@Composable
private fun DirectoryRow(
    path: String,
    fileCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
            contentDescription = if (expanded) "收起目录" else "展开目录",
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            path,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "$fileCount 个文件",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
}

@Composable
private fun MappingRow(index: Int, from: String, to: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 42.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(29.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(index.toString(), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(from, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text("→  $to", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 42.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
    )
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
private fun PanelCard(
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(if (compact) 16.dp else 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(if (compact) 13.dp else 16.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ModeSelector(
    selected: OperationMode,
    onSelected: (OperationMode) -> Unit,
    compact: Boolean,
    enabled: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(if (compact) 3.dp else 4.dp),
    ) {
        ModeButton("隐匿文件名", OperationMode.Transform, selected, onSelected, Modifier.weight(1f), compact, enabled)
        ModeButton("恢复文件名", OperationMode.Restore, selected, onSelected, Modifier.weight(1f), compact, enabled)
    }
}

@Composable
private fun ModeButton(
    text: String,
    mode: OperationMode,
    selected: OperationMode,
    onSelected: (OperationMode) -> Unit,
    modifier: Modifier,
    compact: Boolean,
    enabled: Boolean,
) {
    val active = mode == selected
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled) { onSelected(mode) },
        color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (active) 2.dp else 0.dp,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text,
            Modifier.padding(vertical = if (compact) 8.dp else 10.dp),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ThreadSettings(controller: AppController) {
    val settings = controller.settings
    val enabled = !controller.isBusy
    Spacer(Modifier.height(8.dp))
    Text("多线程", style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThreadMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = settings.threadMode == mode,
                enabled = enabled,
                onClick = { controller.updateSettings(settings.copy(threadMode = mode)) },
                shape = SegmentedButtonDefaults.itemShape(index, ThreadMode.entries.size),
            ) { Text(mode.label) }
        }
    }
    if (settings.threadMode == ThreadMode.On) {
        OutlinedTextField(
            value = controller.workerInput,
            onValueChange = controller::updateWorkerInput,
            label = { Text("并发数（1–$MAX_WORKERS）") },
            isError = !controller.validWorkers,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (settings.threadMode == ThreadMode.Auto) {
        Text("此设备最多同时处理 ${controller.autoWorkers} 项", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OptionRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    compact: Boolean,
    enabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onCheckedChange, enabled = enabled)
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
        append("\n执行用时 ${report.elapsedMs / 1000}.${(report.elapsedMs % 1000).toString().padStart(3, '0')} 秒")
        if (report.stagedFiles > 0) append("，其中 ${report.stagedFiles} 个文件使用了临时名")
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
