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

项目只支持一种固定格式，不包含 GBK、旧 Java 清单或历史 JSON 兼容代码：

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

原生安装包必须在目标操作系统构建。

## 绑定 GitHub 仓库

仓库建议直接命名为 `NameVeil`。当前应用 ID 是 `com.ronglin.nameveil`；如果将来准备发布到应用商店，请在第一次正式发布前确定最终 ID，发布后不要再修改。

```bash
git init
git add .
git commit -m "Initial NameVeil release"
git branch -M main
git remote add origin git@github.com:<你的用户名>/NameVeil.git
git push -u origin main
```

也可以把 SSH 地址替换为 GitHub 提供的 HTTPS 地址。

## GitHub Releases

[`.github/workflows/release.yml`](.github/workflows/release.yml) 会在推送 `v*.*.*` 标签时并行生成：

- `NameVeil-<版本>-android.apk`
- `NameVeil-<版本>-windows-x64.msi`
- `NameVeil-<版本>-windows-x64-portable.zip`
- `NameVeil-<版本>-linux-x64.deb`

随后自动创建 GitHub Release、生成 Release Notes 并上传四个文件。便携 ZIP 解压后直接运行
`NameVeil/NameVeil.exe`，无需安装，但不能只单独复制 EXE，旁边的 `app/` 和 `runtime/` 也必须保留。
发布流程会先运行全部桌面端测试，并检查标签版本与 `composeApp/build.gradle.kts` 中的
`packageVersion` 一致，避免把内部版本错误的安装包发布出去。

### Android 签名配置

首次发布前在本地生成并妥善备份 keystore：

```bash
keytool -genkeypair -v \
  -keystore nameveil-release.jks \
  -alias nameveil \
  -keyalg RSA -keysize 2048 -validity 10000
```

把 keystore 转为 Base64，然后配置四个 GitHub Actions Secrets：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore 的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 例如 `nameveil` |
| `ANDROID_KEY_PASSWORD` | key 密码 |

PowerShell 生成 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("nameveil-release.jks")) |
  Set-Clipboard
```

Linux：

```bash
base64 -w 0 nameveil-release.jks
```

`*.jks` 与 `*.keystore` 已加入 `.gitignore`。不要提交或遗失 keystore，否则后续 Android 版本无法覆盖安装。

### 发布版本

```bash
git tag v1.0.0
git push origin v1.0.0
```

也可以在 GitHub Actions 页面手动运行 Release workflow 并填写标签。

## 项目结构

```text
brand/                 # 原创 SVG 与仓库展示图标
.github/workflows/     # Android / Windows / Linux 自动发布
composeApp/src/
├── commonMain/        # Compose UI、固定配置协议、事务改名引擎
├── commonTest/        # 往返、密码找回与回滚测试
├── androidMain/       # Android SAF 与 Android 加密实现
├── desktopMain/       # Windows/Linux GUI、CLI 与 JVM 文件系统
└── desktopTest/       # AES、PBKDF2 与 headless CLI 端到端测试
```

当前品牌图标由 `gpt-image-2` 生成并经过小尺寸、透明边缘与平台裁切验证：

- [`brand/nameveil-icon-generated.png`](brand/nameveil-icon-generated.png)：仓库与桌面端主图标。
- [`brand/nameveil-icon-foreground.png`](brand/nameveil-icon-foreground.png)：Android 自适应图标透明前景。
- [`brand/nameveil-icon.svg`](brand/nameveil-icon.svg)：最初的原创矢量回退版本，便于以后完全手工调整。

Android、Windows 与 Linux 构建都使用同一“文件 + 遮罩 + 青色 V 形揭示区”视觉标志。

nameveil-release.jks 密钥是6位数字密码(QQ)
正在为以下对象生成 2,048 位RSA密钥对和自签名证书 (SHA256withRSA) (有效期为 10,000 天):
         CN=XXXXXXX XXXXX, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown
