# 新闻推送（NewsPush）

一个面向 Android 的新闻聚合与推送应用：定时抓取网络新闻，先在本地做摘要、分类和重要度评分，再把重要新闻以本地通知的方式推给用户，并附上原文链接。界面保持简洁，支持按分区浏览。

## 功能

- 定时抓取：WorkManager 周期任务，默认每 30 分钟一次（可在设置中改为 15 / 30 / 60 / 120 分钟，且只在联网时运行）。
- 自动摘要：默认调用 DeepSeek API 生成摘要；未配置 Key 或调用失败时，回退到本地抽取式摘要（词频 + 句首权重）。
- 自动分类：默认由 DeepSeek 从候选分区中选择；同样支持本地关键词分类兜底，覆盖国内、国际、财经、科技、编程、体育、娱乐、健康、军事、科学、社会等分区。
- 重要度评分：综合“突发 / 重大 / 官方 / 暴跌”等强信号词、订阅源权威权重和标题质量，超过阈值才推通知。
- 本地推送：通过通知渠道发送摘要式通知，点击回到应用。
- 原文链接：详情页可直接跳转原文。
- 可配置：分区开关、通知开关、重要度阈值、自定义 RSS 源均可调整。
- 单轮处理上限：配置了 DeepSeek 时每轮最多处理 20 条（控制费用与耗时）；只用本地算法时放宽到 200 条。
- 推送时段：可设定每天只在某个时段内推送（支持跨零点，如 22:00 - 06:00）。时段外抓到的重要新闻不会丢，会保持“未推送”状态，等时段内下一次同步时一起推送。
- 黑名单：屏蔽词命中标题、摘要或正文即过滤；也可以按来源屏蔽。新增黑名单后会顺带清理库里已经存在的匹配内容（收藏的除外）。

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Room（本地数据库）
- DataStore Preferences（设置持久化）
- WorkManager（定时任务）
- OkHttp + XML Pull Parser（RSS / Atom 抓取解析）
- DeepSeek Chat Completions API（摘要与分类，OpenAI 兼容协议）

## 如何构建

本机已经装好一套命令行构建环境，位于 `D:\AndroidDev`：

| 组件 | 路径 | 版本 |
| --- | --- | --- |
| JDK | `D:\AndroidDev\jdk` | Temurin 17.0.20.1 |
| Android SDK | `D:\AndroidDev\android-sdk` | platform-tools、platforms;android-35、build-tools;35.0.0 |
| Gradle | `D:\AndroidDev\gradle` | 8.9 |
| Gradle 缓存 | `D:\AndroidDev\gradle-home` | — |

一键编译（会设置好 JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\code\news\build_apk.ps1
```

连接手机（开启 USB 调试）后直接安装到设备：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\code\news\build_apk.ps1 -Install
```

产物位于 `app\build\outputs\apk\debug\app-debug.apk`。

也可以用 Android Studio（Ladybug 或更新版本）打开本目录，它会自动识别 `local.properties` 中的 SDK 路径。

构建仓库已配置国内镜像以加速依赖下载：`settings.gradle.kts` 里优先使用阿里云 Maven 镜像，Gradle wrapper 的分发包指向腾讯云镜像。如需改回官方源，删除这两个文件里的镜像配置即可。

## 把 APK 装到手机

方式一：局域网下载（手机和电脑连同一个 WiFi，不用数据线）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\code\news\serve_apk.ps1
```

脚本会打印形如 `http://192.168.x.x:8000/` 的地址，用手机浏览器打开就能下载安装。默认提供最近一次构建的包（release 优先，其次 debug）。停止服务：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\code\news\stop_apk_server.ps1
```

如果手机打不开页面，通常是 Windows 防火墙挡了入站连接，用**管理员**身份执行一次即可：

```powershell
New-NetFirewallRule -DisplayName "NewsPush APK Debug Server 8000" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8000 -Profile Any
```

方式二：USB 安装（手机需开启 USB 调试）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\code\news\build_apk.ps1 -Install
```

方式三：手动拷贝

把 `app\build\outputs\apk\debug\app-debug.apk` 复制到手机的「下载」目录，在手机上点开安装；首次会提示「禁止安装未知应用」，选择允许即可。

注意：当前是 debug 测试包，未做正式签名，适合自己安装验证，不适合上架或对外分发。

## 签名与发布

release 构建使用自签名 keystore，签名配置放在项目根目录的 `keystore.properties`（已 gitignore，不会进版本库），模板见 `keystore.properties.example`。

| 项目 | 值 |
| --- | --- |
| keystore | `D:\AndroidDev\keystore\newspush-release.jks` |
| 别名 | `newspush` |
| 密钥算法 | RSA 4096，有效期 10000 天 |
| 证书 SHA-256 | `1F:33:B3:2C:4C:61:8B:67:15:5E:CE:3F:6F:60:B2:15:CE:51:22:1B:E5:27:E6:1F:91:A6:16:C6:BD:8D:49:82` |
| 口令记录 | `D:\AndroidDev\keystore\keystore-credentials.txt` |

构建正式包：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\code\news\build_apk.ps1 -Task assembleRelease
```

产物：`app\build\outputs\apk\release\app-release.apk`（v2 签名，未开启代码混淆）。

两个注意点：

1. **keystore 和口令文件必须备份在一起**。以后所有版本更新都要用同一个签名，丢了就只能卸载重装，用户数据会丢失。
2. **release 包和 debug 包签名不同，不能互相覆盖安装**。手机上如果已经装了 debug 版，想换 release 版需要先卸载（App 数据、DeepSeek Key、黑名单都会被清掉）。

## 目录结构

- `data/model`：实体与分区枚举
- `data/source`：RSS 源定义与抓取解析
- `data/local`：Room 数据库
- `data/prefs`：设置持久化
- `data/repository`：数据仓库与同步逻辑
- `domain`：摘要、分类、重要度评分
- `work`：定时任务、调度与通知
- `ui`：Compose 界面

## 默认订阅源

默认内置的源都在 2026-10 实测过，能拉到当天或前一天的内容：

| 分区 | 来源 |
| --- | --- |
| 国内 | 中国新闻网·滚动 |
| 国际 | 中国新闻网·国际 |
| 科技 | IT之家、Solidot、少数派 |
| 编程 | 博客园、开源中国、InfoQ 中文、掘金、美团技术团队、阮一峰的网络日志、Hacker News、DEV Community |

没有采用的原因（都可在设置页自行加回）：人民网 `politics.xml` 的 RSS 停更在 2025-06，新浪科技 RSS 停在 2018，果壳返回 404，36氪 / 虎嗅 / 纽约时报中文 / BBC 中文在本机网络下返回空或连不通。

只保留 **14 天内**发布的新闻：同步时会跳过更早的条目，并清理库里早于该窗口的内容（收藏的除外），所以不会再出现陈年旧闻。

需要复测订阅源可用性时，跑 `scripts/check_feeds.ps1`，它会打印每个源的 HTTP 状态、条目数和最新发布时间。

部分订阅源使用 HTTP 明文地址，因此 `AndroidManifest.xml` 开启了 `usesCleartextTraffic`。若只使用 HTTPS 源，可以关闭该开关。

## 待你确认的重要决策

当前版本默认方案如下。你已确认第 2 点接入 DeepSeek（留空 Key 时自动回退本地算法），其余三点如有不同偏好告诉我即可调整：

1. 新闻来源：默认 RSS（免费、无需密钥、可配置）。是否改用 NewsAPI、定向爬虫或自建后端？
2. 摘要与分类：已确认接入 DeepSeek API（在设置页填写 API Key 后生效，留空自动回退本地算法）。
3. 推送方式：默认本地通知（无需服务器，进程被回收后由系统调度）。是否改为 FCM / 自建后端真推送？
4. 分区与语言：默认中文分区。是否需要调整分区集合、默认抓取间隔或重要度阈值？
