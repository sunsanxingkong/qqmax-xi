# QQ Max xi2.6

手表版 QQ Lite 魔改包 **QQ Max M2.5** 的增强移植源码：把 Xposed 模块「QQMax 增强模块 1.2.2」的功能**直接改包移植**进宿主 APK（不依赖 Xposed 框架），外加群管理菜单、网页 CK 禁言与宿主风格的设置界面。

- 宿主：`com.tencent.qqlite`（QQ Max M2.5 / versionCode 2465，原包为 AOSP testkey 签名）
- 本版本：versionName **xi2.6**，最新构建 **fix10**（2026-11-20）
  - `QQ_Max_xi2.6_fix10.apk` MD5 `63f3fdcd50a0856e1b429c66a1c34e22`
  - 与原包同证书（AOSP testkey），可直接覆盖安装

> 本仓库只包含**移植部分的 Java 源码、编译桩、构建工具与分析文档**，不包含宿主 APK 本体与反编译出的宿主代码。仅供学习研究，请勿用于商业用途；使用风险自负。

## 已移植功能

| 功能 | 说明 |
|---|---|
| 大表情修复 | `BigfaceRes` 资源自愈（标记文件在但资源缺失时重新解压）+ 表情面板缓存失效条件修复 |
| 防撤回 | mode 1 强制阻止撤回（拦截删除事件）；mode 2 仅记录 |
| 群管理菜单 | 群聊长按成员头像/昵称弹出：禁言（10 分钟 / 1 小时 / 1 天 / 7 天 / 30 天 / 自定义）、解除禁言、全员禁言、解除全员禁言、踢出群聊 |
| 网页禁言 | 单人禁言走网页群管理接口（`qun.qq.com/cgi-bin/qun_info/set_group_shutup`），使用者在设置里自填网页登录 CK（Cookie，需含 `uin` 与 `skey`）。手表版内核的单人禁言命令被服务端按产品身份拒绝（-10122），故走网页通道 |
| 全员禁言 / 踢人 | 走宿主内核接口 `setGroupShutUp` / `kickMember`，结果以服务器返回为准 |
| 群公告全量 | 打开群公告时自动翻页拉取全部公告 |
| 图片保存 | 图片查看器长按保存到 `Pictures/QQ Max` |
| 语音波形防崩 | `PttAudioWaveView` 崩溃修复 |
| 消息后缀 | 发出的文字消息末尾追加自定义后缀（默认关） |
| 自动回复 | 私聊收到消息自动回复，同一会话 60 秒冷却 |
| 关键词回复 | 规则行「关键词\|回复\|会话」；回复填 `AI` 走关键词 AI 接口 |
| 定时消息 | 规则行「HH:mm,类型,uid,内容」，到点自动发送 |
| AI 总结自定义模型 | 可配置接口地址 / API Key / 模型，未配置走原版 |
| 应用内设置 | 聊天设置页与 WearQQ 设置页各有一个入口；界面按宿主设置页风格（圆角卡片、蓝色分区标题、深色输入弹窗），全部配置即时生效 |

配置统一存放在宿主 `filesDir/qqlite_settings.json`（与原 Xposed 模块同名同格式，老用户配置可直接沿用）。

## 未移植

- 防撤回 mode 2 的灰条原文渲染 / 红边角标
- 伪造图片/视频/文件消息、翻译替换（宿主无对应入口）、选择器类功能、加好友入口、验证问答、临时会话、邮箱、QQ 动态、HTTP API、JS 插件系统、局部文本可选
- 模块的作者公告与官方群门禁属于作者控制机制，未移植

## 仓库结构

```
src/momoi/mod/qqpro/qqmax/   移植功能的全部 Java 源码（编入宿主 classes7.dex）
stubs/                       编译期桩（宿主内核接口签名，仅供 javac 通过，不打包）
tools/SmaliTool.java         smali 汇编/反汇编驱动（配合 apktool.jar 使用）
docs/analysis-bigface.md     大表情（bigface）问题定位报告
docs/module-analysis.md      「QQMax 增强模块 1.2.2」功能分析报告（32 项功能分级）
```

## 构建方法（概要）

1. 准备：JDK 21、Android SDK build-tools 34、apktool 2.11.1。
2. 编译移植源码（注意 `-parameters`，否则 d8 会在匿名类上 NPE）：

   ```bash
   javac -parameters -source 8 -target 8 \
     -bootclasspath platforms/android-34/android.jar \
     -d out $(find stubs src -name "*.java")
   d8 --min-api 21 --output dexout $(find out/momoi -name "*.class")
   ```

   d8 产物里剔除 stubs，只取 `momoi.mod.qqpro.qqmax.*` 的 smali 合并进宿主 `classes7.dex` 的 smali 树后重汇编。
3. 宿主侧钩子点（消息删除拦截、渲染还原、发送路径、公告接口、设置页入口等）的 smali 修改点见 `docs/` 两份报告与源码内注释。
4. 重打包：以原包为底，仅替换变更的 dex 条目与 `AndroidManifest.xml`（versionName 改 xi2.6 为字符串池定点改写，versionCode 不变），`zipalign` 后用原包同一 testkey 签名（v1+v2+v3）。

> 注意：`invoke-static` 短格式的参数寄存器必须 ≤ v15，超限会被 smali 汇编静默丢弃——汇编后务必检查日志中的 "Invalid register"，并在最终 dex 上核对调用数量。

## 已知限制

- 全部功能以静态验证 + 构建产物核对为准，逐项真机验证情况以发布说明为准。
- 网页禁言依赖使用者自己的网页 CK，CK 过期需重新获取粘贴；禁言/踢人结果由服务器决定（须为群主/管理员）。
- 单人禁言的内核通道对手表版产品未开放（服务器 -10122），客户端无解，这也是网页通道存在的原因。
