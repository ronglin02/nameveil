package com.ronglin.nameveil

import com.ronglin.nameveil.core.OperationMode
import com.ronglin.nameveil.core.RenamePlan
import com.ronglin.nameveil.core.MAX_WORKERS
import com.ronglin.nameveil.core.automaticWorkers
import java.nio.file.Path

object NameVeilCli {
    const val VERSION = APP_VERSION

    suspend fun run(args: Array<String>, gateway: DesktopStorageGateway): Int {
        if (args.isEmpty() || args.first() in setOf("help", "--help", "-h")) {
            printHelp()
            return 0
        }
        if (args.first() in setOf("--version", "version")) {
            println("NameVeil $VERSION")
            return 0
        }

        return try {
            val verb = args.first().lowercase()
            val options = parseOptions(args.drop(1))
            val location = gateway.locationFor(Path.of(options.directory))
            val engine = com.ronglin.nameveil.core.RenameEngine(gateway, gateway, options.workers)

            if (verb in setOf("password", "show-password", "recover-password")) {
                println(engine.recoverPassword(location, options.recursive))
                return 0
            }

            val command = when (verb) {
                "protect", "transform" -> OperationMode.Transform
                "restore", "recover" -> OperationMode.Restore
                else -> throw CliException("未知命令：${args.first()}。使用 --help 查看帮助。")
            }
            val password = readPassword(options)
            val plan = engine.createPlan(
                location = location,
                mode = command,
                password = password,
                includeSubdirectories = options.recursive,
                preserveExtension = options.keepExtension,
            )

            printPreview(plan)
            if (options.dryRun) {
                println("\n仅预览：未修改任何文件。")
                return 0
            }
            if (!options.assumeYes && !confirm()) {
                println("已取消，未修改任何文件。")
                return 0
            }

            var lastDirectory = -1
            val report = engine.execute(plan) { progress ->
                if (options.progress && progress.currentDirectory.isNotEmpty() && progress.completedDirectories != lastDirectory) {
                    lastDirectory = progress.completedDirectories
                    println("[${progress.completedDirectories + 1}/${progress.totalDirectories}] ${progress.currentDirectory}")
                }
            }
            println("\n完成目录：${report.completedDirectories}，改名文件：${report.renamedFiles}")
            report.warnings.forEach { println("警告：$it") }
            report.failures.forEach { println("失败：$it") }
            if (report.isSuccess) 0 else 1
        } catch (error: Exception) {
            System.err.println("错误：${error.message ?: error::class.simpleName}")
            2
        }
    }

    private fun parseOptions(arguments: List<String>): CliOptions {
        var directory: String? = null
        var recursive = true
        var keepExtension = false
        var workers = automaticWorkers()
        var progress = true
        var dryRun = false
        var assumeYes = false
        var password: String? = null
        var passwordEnvironment = "NAMEVEIL_PASSWORD"
        var index = 0

        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "--no-recursive" -> recursive = false
                "--recursive" -> recursive = true
                "--keep-extension" -> keepExtension = true
                "--no-progress" -> progress = false
                "--threads" -> {
                    val count = arguments.getOrNull(++index)
                    workers = when (count) {
                        "auto" -> automaticWorkers()
                        "off" -> 1
                        else -> count?.toIntOrNull()?.takeIf { it in 1..MAX_WORKERS }
                            ?: throw CliException("--threads 需要 auto、off 或 1–$MAX_WORKERS")
                    }
                }
                "--dry-run" -> dryRun = true
                "--yes", "-y" -> assumeYes = true
                "--password" -> {
                    index++
                    password = arguments.getOrNull(index)
                        ?: throw CliException("--password 后缺少密码")
                }
                "--password-env" -> {
                    index++
                    passwordEnvironment = arguments.getOrNull(index)
                        ?: throw CliException("--password-env 后缺少环境变量名")
                }
                else -> {
                    if (argument.startsWith('-')) throw CliException("未知选项：$argument")
                    if (directory != null) throw CliException("只能指定一个目录")
                    directory = argument
                }
            }
            index++
        }

        return CliOptions(
            directory = directory ?: throw CliException("缺少要处理的目录"),
            recursive = recursive,
            keepExtension = keepExtension,
            workers = workers,
            progress = progress,
            dryRun = dryRun,
            assumeYes = assumeYes,
            password = password,
            passwordEnvironment = passwordEnvironment,
        )
    }

    private fun readPassword(options: CliOptions): String {
        options.password?.let {
            require(it.isNotEmpty()) { "密码不能为空" }
            return it
        }
        System.getenv(options.passwordEnvironment)?.takeIf { it.isNotEmpty() }?.let { return it }

        val console = System.console()
            ?: throw CliException(
                "当前环境无法安全读取密码；请设置 ${options.passwordEnvironment}，" +
                    "或使用 --password（不推荐，可能进入 shell 历史）。",
            )
        val characters = console.readPassword("配置密码：")
            ?: throw CliException("未读取到密码")
        return try {
            String(characters).also { require(it.isNotEmpty()) { "密码不能为空" } }
        } finally {
            characters.fill('\u0000')
        }
    }

    private fun printPreview(plan: RenamePlan) {
        val action = if (plan.mode == OperationMode.Transform) "隐匿" else "恢复"
        println("NameVeil $VERSION · $action 预览")
        println("目录：${plan.location.displayPath}")
        println("涉及目录：${plan.directories.size}，文件：${plan.mappings.size}")
        println()
        plan.mappings.take(PREVIEW_LIMIT).forEach { mapping ->
            println("${mapping.directoryPath}: ${mapping.fromName} -> ${mapping.toName}")
        }
        if (plan.mappings.size > PREVIEW_LIMIT) {
            println("……其余 ${plan.mappings.size - PREVIEW_LIMIT} 条未显示")
        }
    }

    private fun confirm(): Boolean {
        val console = System.console()
            ?: throw CliException("非交互环境执行时必须显式添加 --yes；可先使用 --dry-run 检查。")
        return console.readLine("\n确认执行？输入 yes 继续：")?.trim()?.lowercase() == "yes"
    }

    private fun printHelp() {
        println(
            """
            NameVeil $VERSION — 完全离线的批量文件名隐匿/恢复工具

            用法：
              NameVeil protect <目录> [选项]
              NameVeil restore <目录> [选项]
              NameVeil password <目录> [--no-recursive]
              NameVeil --gui

            选项：
              --dry-run              只生成预览，不修改文件
              --yes, -y              非交互确认执行
              --no-recursive         只处理指定目录，不扫描子目录
              --keep-extension       保留扩展名（会降低对后缀扫描的规避效果）
              --threads <auto|off|N> 自动、串行或指定并发数（1–$MAX_WORKERS）
              --no-progress          隐藏终端执行进度
              --password-env <名称>  从环境变量读取密码，默认 NAMEVEIL_PASSWORD
              --password <密码>      直接传入密码（不推荐，可能进入 shell 历史/进程列表）
              --help, -h             显示帮助
              --version              显示版本

            示例：
              export NAMEVEIL_PASSWORD='your password'
              NameVeil protect /data/archive --dry-run
              NameVeil protect /data/archive --yes
              NameVeil password /data/archive
              NameVeil restore /data/archive --yes

            默认会递归处理并移除所有扩展名。NAMEVEIL 配置保存在每个被处理目录内，
            因而整个目录可以复制到另一台离线设备后用相同密码恢复。
            """.trimIndent(),
        )
    }

    private data class CliOptions(
        val directory: String,
        val recursive: Boolean,
        val keepExtension: Boolean,
        val workers: Int,
        val progress: Boolean,
        val dryRun: Boolean,
        val assumeYes: Boolean,
        val password: String?,
        val passwordEnvironment: String,
    )

    private class CliException(message: String) : IllegalArgumentException(message)
    private const val PREVIEW_LIMIT = 100
}
