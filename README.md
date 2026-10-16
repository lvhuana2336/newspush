# 新闻推送（NewsPush）

一个 Android 端的新闻聚合与推送应用：定时从 RSS 源抓取新闻，自动生成摘要并分类，按重要度筛选后以本地通知推送。通知和详情页都保留原文链接，点开即可跳转。

界面保持简洁——首页按分区筛选浏览，详情页看摘要与正文，设置页调整抓取与推送行为。安装包见 [Releases](https://github.com/lvhuana2336/newspush/releases) 页面，也可以按下面的步骤自行构建。

> 项目为中文界面与中文分区，默认订阅源也以中文媒体为主。

## 功能

- **定时抓取**：WorkManager 周期任务，默认 30 分钟一次（可选 15 / 30 / 60 / 120 分钟），仅在联网时运行。
- **自动摘要与分类**：配置 DeepSeek API Key 后由 DeepSeek 生成摘要并选择分区；未配置或调用失败时自动回退到本地算法（抽取式摘要 + 关键词分类 + 重要度评分），可离线工作。
- **分区浏览**：国内、国际、财经、科技、编程、体育、娱乐、健康、军事、科学、社会，共 11 个分区，每个都能单独开关。
- **重要度筛选**：结合强信号词（突发、重大、官方、暴跌 等）、来源权重与标题质量打分，只有超过阈值的新闻才推送，阈值可调。
- **推送时段**：可限定每天只在某个时段推送，支持跨零点（如 22:00–06:00）。时段外抓到的重要新闻不会丢弃，会保留到时段内的下一次同步补推。
- **黑名单**：支持屏蔽词（命中标题、摘要或正文即过滤）与来源屏蔽；新增后会顺带清理库中已存在的匹配内容（收藏除外）。
- **原文链接**：列表与详情页均可跳转原文。
- **时效过滤**：只保留 14 天内发布的新闻，超窗条目在同步时清理（收藏除外）。
- **单轮处理上限**：使用 DeepSeek 时每轮最多处理 20 条新条目（控制费用与延迟），纯本地模式放宽到 200 条。

## 技术栈

| 组件 | 用途 |
| --- | --- |
| Kotlin + Jetpack Compose（Material 3） | 界面 |
| Room | 本地数据库 |
| DataStore Preferences | 设置持久化 |
| WorkManager | 定时抓取与后台任务 |
| OkHttp + XmlPullParser | RSS / Atom 抓取与解析 |
| DeepSeek Chat Completions API | 摘要与分类（可选，OpenAI 兼容协议） |

最低支持 Android 8.0（API 26），编译与目标 SDK 为 35。

## 下载安装

最新安装包发布在 [Releases](https://github.com/lvhuana2336/newspush/releases) 页面，文件名形如 `newspush-1.0-release.apk`。

下载后在手机上点开安装，首次需要在系统里允许「安装未知应用」。最低系统版本 Android 8.0（API 26）。

> 发布包由作者自签名，与 debug 构建签名不同，两者无法互相覆盖安装。安装包内不含任何 API Key。

## 快速开始（自行构建）

### 环境要求

- JDK 17
- Android SDK，含 `platforms;android-35` 与 `build-tools;35.0.0`
- 可选：Android Studio Ladybug 或更新版本

### 构建

```bash
git clone https://github.com/lvhuana2336/newspush.git
cd newspush

# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

如果命令行找不到 SDK，在项目根目录新建 `local.properties`（已被 gitignore）：

```properties
sdk.dir=/path/to/Android/Sdk
```

也可以直接用 Android Studio 打开项目，同步完成后点运行。

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或者把 APK 拷进手机存储，用文件管理器点开安装（首次需要在系统里允许「安装未知应用」）。

debug 包与 release 包使用不同签名，两者无法互相覆盖安装。

### 依赖下载加速（可选）

仓库默认把依赖源指向国内镜像，在国内网络下更快：`settings.gradle.kts` 中优先使用阿里云 Maven 镜像，Gradle wrapper 的分发包指向腾讯云镜像。在海外网络下可以改回官方源——删除 `settings.gradle.kts` 里 `maven.aliyun.com` 的三行仓库配置，并把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 换成 `https://services.gradle.org/distributions/gradle-8.9-bin.zip`。

## 使用与配置

安装后进入设置页，可调整以下内容：

| 设置项 | 说明 |
| --- | --- |
| 推送通知 | 通知总开关 |
| 推送时段 | 限定每天的推送时间窗，可跨零点 |
| 抓取间隔 | 15 / 30 / 60 / 120 分钟 |
| 重要度阈值 | 越高越只推重点新闻 |
| DeepSeek API Key | 填入后启用云端摘要与分类，留空使用本地算法 |
| 新闻分区 | 按分区开关抓取与展示 |
| 黑名单 | 屏蔽词与来源屏蔽 |
| 自定义 RSS | 添加自己的 RSS / Atom 源，分类由文本自动判断 |

关于 DeepSeek：Key 保存在应用私有目录的 DataStore 中，除 DeepSeek 官方接口外不会发送到别处，也不会写入日志。启用后每轮抓取最多处理 20 条新条目，用于控制费用。

## 默认订阅源

| 分区 | 来源 |
| --- | --- |
| 国内 | 中国新闻网·滚动 |
| 国际 | 中国新闻网·国际 |
| 科技 | IT之家、Solidot、少数派 |
| 编程 | 博客园、开源中国、InfoQ 中文、掘金、美团技术团队、阮一峰的网络日志、Hacker News、DEV Community |

RSS 地址由源站自行维护，可能随时改版、限流或停更。一些常见的旧源已失效，因此没有被内置：人民网时政的 RSS 停更在 2025 年 6 月，新浪科技停留在 2018 年，果壳返回 404，36氪 / 虎嗅 / 纽约时报中文 / BBC 中文在部分网络环境下不可用。遇到失效的源，可以在设置页删掉换一个，或直接修改 `app/src/main/java/com/newspush/app/data/source/DefaultFeeds.kt`。

部分源使用 HTTP 明文地址，所以 `AndroidManifest.xml` 开启了 `usesCleartextTraffic`。若只使用 HTTPS 源，可以关闭该开关。

## 签名与发布

仓库不包含任何签名密钥，发布自己的构建需要先生成 keystore：

```bash
keytool -genkeypair -v -keystore release.jks -storetype PKCS12 \
  -alias newspush -keyalg RSA -keysize 4096 -validity 10000
```

然后在项目根目录创建 `keystore.properties`（已被 gitignore，模板见 `keystore.properties.example`）：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=你的口令
keyAlias=newspush
keyPassword=你的口令
```

之后执行 `gradlew.bat assembleRelease`（Windows）或 `./gradlew assembleRelease`，产物在 `app/build/outputs/apk/release/`。

两个容易踩的点：

1. `storeFile` 的路径用正斜杠或双反斜杠。`.properties` 文件里反斜杠是转义符，写成 `D:\keys\app.jks` 时 `\k`、`\a` 会被错误解析，`\n` 甚至会变成换行。
2. release 构建默认未开启代码混淆（`isMinifyEnabled = false`）。想减小体积可以打开 R8，但要自行验证 Room 与 Compose 的 keep 规则。

keystore 和口令请单独备份：后续所有版本更新都必须使用同一份签名，丢失后只能更换包名重新发布。

## 项目结构

```
app/src/main/java/com/newspush/app/
├── data/model       实体与分区枚举
├── data/source      RSS 源定义、抓取与解析
├── data/local       Room 数据库
├── data/prefs       设置持久化
├── data/repository  仓库与同步流程
├── data/remote      DeepSeek 客户端
├── domain           摘要、分类、重要度评分
├── work             定时任务、调度与通知
└── ui               Compose 界面
```

根目录下另有若干 Windows PowerShell 辅助脚本（环境安装、一键构建、局域网分发 APK、订阅源可用性检查、本地网络代理等），脚本内的绝对路径需要按自己的环境调整，非 Windows 环境可以忽略。

## 已知限制

- 财经、体育、娱乐、健康、军事、科学等分区没有专属订阅源，内容来自通用源的文本分类结果。
- 编程分区包含 Hacker News 与 DEV Community 两个英文源。
- 摘要与分类依赖 DeepSeek 时会受网络与额度影响，失败时静默回退到本地算法，不会报错。
- 尚未编写单元测试与 UI 测试。

## 数据与隐私

- 新闻、收藏与设置全部保存在设备本地（Room + DataStore），应用没有统计或上报逻辑。
- 应用只在两种情况下联网：抓取你启用的 RSS 源；配置了 Key 时调用 DeepSeek 接口。
- DeepSeek API Key 以明文形式保存在应用私有目录的 DataStore 里。如果对安全性要求更高，可以自行接入 Android Keystore 加密。

## 许可证

本项目尚未附带开源许可证，因此默认保留所有权利。如需在其它项目中使用，请先联系作者。
