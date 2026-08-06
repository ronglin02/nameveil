<p align="center">
  <img src="brand/nameveil-icon-generated.png" width="144" alt="NameVeil icon" />
</p>

<h1 align="center">NameVeil · 名隐</h1>

<p align="center">完全离线、可预览、可恢复的跨平台文件名隐匿工具</p>

NameVeil 支持 Android、Windows、Linux 桌面，以及没有 GUI 的 Linux 服务器。它把目录中的文件改成无扩展名的 20 位随机名称，并把恢复配置保存在文件所在目录；复制完整目录到另一台客户端后即可继续恢复。

```text
旅行照片.jpg        → 8hm6IIZ7JEL6fw7eQgK8
合同最终版.pdf      → 2FbM9ZsR6cN1kL4pQ7xA
```

## 适用边界

NameVeil 用于规避仅根据原文件名或 `.jpg`、`.pdf` 等扩展名工作的简单扫描逻辑。

> [!IMPORTANT]
> NameVeil 不修改文件内容，因此不是内容加密工具。读取文件头、MIME、媒体元数据或完整内容的软件仍可能识别文件类型。需要对抗内容扫描时，应使用成熟的加密容器或磁盘加密。

运行时完全离线，不访问服务器，不需要账号，也不会把配置存入注册表、用户目录或应用私有数据库。

## 单一配置格式

项目只支持一种固定格式：

| 文件 | 用途 |
| --- | --- |
| `NAMEVEIL` | 已完成操作的正式配置 |
| `NAMEVEIL.pending` | 正在执行时的事务配置；意外中断后用于恢复 |

每个实际包含文件的目录保存自己的配置，复制整个目录树时恢复信息会自然随文件一起移动。

固定外层格式：

```json
{
  "format": "nameveil-config",
  "version": 1,
  "recoveryPassword": "用户输入的密码",
  "kdf": "PBKDF2-HMAC-SHA256",
  "iterations": 310000,
  "salt": "...",
  "cipher": "AES-256-GCM",
  "nonce": "...",
  "ciphertext": "..."
}
```

文件名映射位于 AES-256-GCM 密文中，不会直接出现在配置文本里。PBKDF2 明确使用 UTF-8 密码字节，因此中文密码也能在 Android、Windows 和 Linux 之间恢复。

## 密码找回与“君子模式”

配置中的 `recoveryPassword` 是刻意保留的明文找回字段。忘记密码时：

- GUI：选择“恢复文件名”，选中目录，点击“从配置找回密码”。
- CLI：执行 `NameVeil password <目录>`。

这意味着 NameVeil 的密码用于降低误操作和让配置保持结构化，**不用于对抗能读取配置文件的人**。AES-GCM 的价值是让普通文本扫描看不到原文件名并检测配置损坏，而不是提供真正的保密性。

如果连 `NAMEVEIL` 配置也丢失，随机文件名与原名之间的映射将无法找回，所以复制或备份时必须包含该文件。

## GUI 使用

1. 选择“隐匿文件名”或“恢复文件名”。
2. 选择目录；Android 使用系统目录授权页，桌面端使用目录选择器。
3. 设置是否包含子目录、是否保留扩展名，并输入密码。
4. 生成预览，核对所有文件名映射。
5. 二次确认后执行。

默认递归处理并移除扩展名。“保留扩展名”会降低对后缀扫描的规避效果，因此标记为不推荐。

## 无 GUI Linux / CLI

```bash
NameVeil --help
```

推荐通过环境变量输入密码，避免进入 shell 历史或进程列表：

```bash
export NAMEVEIL_PASSWORD='your password'
```

```bash
# 只预览
NameVeil protect /data/archive --dry-run

# 隐匿
NameVeil protect /data/archive --yes

# 忘记密码时找回
NameVeil password /data/archive

# 恢复
NameVeil restore /data/archive --yes
```

| 选项 | 作用 |
| --- | --- |
| `--dry-run` | 只输出预览，不修改文件 |
| `--yes` / `-y` | 非交互确认；脚本或服务环境必须显式使用 |
| `--no-recursive` | 只处理指定目录 |
| `--keep-extension` | 保留扩展名，不推荐 |
| `--password-env NAME` | 指定密码环境变量，默认 `NAMEVEIL_PASSWORD` |
| `--password VALUE` | 直接传入密码，不推荐 |
| `--gui` | 明确启动桌面 GUI |

## 事务安全

每个目录独立执行：

1. 扫描文件并完成缺失项、重复项和目标名冲突检查。
2. 在当前目录写入包含全部映射的 `NAMEVEIL.pending`。
3. 逐个改名；任一步失败就按相反顺序回滚本目录。
4. 全部成功后将 pending 提交为 `NAMEVEIL`。

恢复时，源文件先统一进入唯一临时名，然后再恢复原名，因此名称交换也不会互相占用。文件全部恢复后才删除配置。

## 平台要求

| 平台 | 要求 | 产物 |
| --- | --- | --- |
| Android | Android 8.0 / API 26 或更高 | 签名 APK |
| Windows | 64 位 Windows | MSI 或自带运行时的应用目录 |
| Linux 桌面/服务器 | 64 位 Linux；CLI 不要求图形会话 | DEB 或自带运行时的应用目录 |

开发环境需要 JDK 17；Android 构建需要 Android SDK Platform 35。Gradle 版本由仓库内 Wrapper 管理。

## 本地开发与构建

```powershell
# 桌面 GUI
./gradlew.bat :composeApp:run

# 测试
./gradlew.bat desktopTest

# Android debug APK
./gradlew.bat :composeApp:assembleDebug

# Windows MSI
./gradlew.bat :composeApp:packageMsi

# Windows 免安装目录（完整目录需要一起复制）
./gradlew.bat :composeApp:createDistributable
```

Linux：

```bash
./gradlew :composeApp:run --args="--help"
./gradlew :composeApp:packageDeb
```
