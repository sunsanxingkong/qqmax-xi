# QQMax增强模块（com.tencent.qqlitepro v1.2.2）静态分析报告

> 目的：为「把模块功能移植进 QQ Max M2.5（com.tencent.qqlite，手表版 QQ NT，已含 momoi/QQPro mod）的 smali 补丁」提供功能清单、hook 点清单与难度分级。
> 方法：jadx 全量反编译模块 classes.dex（627 个自有类）+ dexdump 交叉核对；宿主侧用 M2.5 的 dexdump（c1–c5）与 classes4.dex smali 核对目标类/方法是否存在。
> 约定：结论后标注证据（类名/方法名/字符串原文）；推测明确标注「推测」。

## 0. 模块概况

- 包名 `com.tencent.qqlitepro`，名「QQMax增强模块」v1.2.2/code 9，作者署名 `Made by TraeWork`（MainActivity 字符串）。
- Xposed 入口：`assets/xposed_init` → `com.tencent.qqlitepro.MainHook implements IXposedHookLoadPackage`。
- 自带库：Rhino（org/mozilla/javascript，JS 插件引擎）、JavaMail（javax/mail + com/sun/mail，QQ 邮箱 IMAP/SMTP）、Gson（com/google/gson，配置/缓存/HTTP JSON）。
- 模块自身定位是「AntiRecall 模块」（日志串 `==== AntiRecall 模块已注入 ====`），防撤回是主干，其余为围绕消息监听长出来的自动化/增强功能。

---

## 1. MainHook 初始化流程

证据：`MainHook.java`（jadx 反编译，730 行）。

### 1.1 目标包与进程

- 只处理 `packageName == "com.tencent.qqlite"`（常量 `TARGET_APP = "com.tencent.qqlite"`）；其它包直接 return（日志「跳过非目标包」）。
- 进程分流（`handleLoadPackage` 开头）：
- `processName` 以 `:MSF` 结尾 → 只装 `RiskControlBypassHook.patchMsfProcess(param)` + `MsfKeepAliveHook.patchMsfProcess(param)` 后 return。
- 主进程判定：`processName` 必须等于 `com.tencent.qqlite` 或 `com.tencent.mobileqq`（原文如此，两个都放行），否则「跳过非主进程」。
- 也就是说：**除 MSF 子进程外，所有功能都在主进程**；没有独立进程组件（模块的 StatusProvider 在模块 App 进程，见 1.4）。

### 1.2 无条件先装（门禁之前）

主进程一进来就装，与官方群门禁无关：

| 调用 | 作用 |
|---|---|
| `hookApplicationForStatus(param)` | hook `ContextWrapper.attachBaseContext(Context)` 与 `Application.onCreate()`（均 after）→ `Status.reportInjected(ctx)` 向模块 App 上报已注入；同时置位 `sHostAppReady`；另有兜底线程 `reportStatusFallback`（线程名 `antirecall-status-fallback`，60s 内轮询 `MsgCache.appContext()`） |
| `SettingsInjector.patch(cl)` | 设置入口注入（见 §2.1）——**未过门禁时只有这个设置入口可用** |
| `QqKernel.setClassLoader(cl)` | 保存宿主 ClassLoader 供全模块反射用 |

### 1.3 官方群门禁（作者的分发控制，移植时应剔除）

- 若 `sGroupPassed == false` → `startGroupGate(param)` 后 return，**不装任何功能 hook**：
- hook `Activity.onResume`（after）→ 每次前台恢复触发 `checkGroupAndInstall`（3 秒节流，常量 `GATE_CHECK_INTERVAL_MS=3000`，后台线程 `antirecall-group-gate`）。
- 等宿主就绪（`awaitHostReady(60000)`：`Application.onCreate` 已跑 + 首个 Activity 已 onResume）后调用 `QqKernel.queryOfficialGroupState(60000)`：查内核群列表/会话列表里是否含官方群 **`OFFICIAL_GROUP_CODE = 801524531`**（QqKernel 常量；MainActivity 文案「二维码加载失败，请前往官方群聊 801524531」，二维码图 `https://yuju.99kpk.top:81/pan/1610/qrcode_1790274464464.jpg`）。
- 返回 TRUE → `onGatePassed`：置位后**重新调用一遍 `handleLoadPackage`** 走完整安装 + `catchUpLateInject()`（补装强制公告/公告监听/刷新会话列表）。
- 返回 FALSE → Toast「您尚未加入官方群聊，请前往模块界面扫码进群后重启应用」（常量 `NOT_IN_GROUP_TIP`）；返回 null（内核未就绪）→ 不判定，等下次 onResume。

### 1.4 门禁通过后的完整安装清单（handleLoadPackage 后半段，顺序即源码顺序）

无单独 Config 开关拦截安装；各功能在**回调运行时**读 Config 决定是否生效（见各条）。安装即 hook，开关在回调里判断——这是本模块的统一模式。

1. `RiskControlBypassHook.patch(param)`（主进程风控绕过）
2. `MsfKeepAliveHook.patchMainProcess(param)`（推送保活主进程侧）
3. `loadCacheWhenReady()` → `MsgCache.get().load(ctx)`（读 `filesDir/antirecall_cache.json`）+ `PluginEngine.ensureLoaded(ctx)` + 状态上报
4. `MsgListenerHook.hook(MsgListenerHook.findListener(cl))`（当前会话消息监听）
5. `MsgListenerHook.hookGlobal(cl)`（全局消息监听）
6. `UiPatcher.patch(cl)`（启动样式对账线程）/`patchGrayTipCell` / `patchMsgFactory` / `patchBaseCell`（含 `patchPicCell` 与 `FakeMediaReceiver.init`）/ `patchLongPressMenu` / `patchPttWaveView`
7. `ChatCardInjector.patch(cl)`（邮箱会话卡片）
8. `MediaPickerHook.patch(cl)`（系统媒体选择器 + 添加好友入口）
9. `SettingsInjector.patchSelectableText(cl)`（文本可选修复）
10. `QqMailManager.ensurePolling(null)`（邮箱轮询，内部等验证通过才真正轮询）
11. `ForceNoticeInjector.patch(cl)`（作者强制公告）
12. `AiSummaryHook.patchSummarizeApi(cl)`（AI 总结接管）
13. `TranslateHook.patch(cl)`（翻译接口替换）
14. `QqKernel.init(cl)` + `HttpApiServer.start()`（HTTP API 服务）
15. `QqKernel.registerGlobalBulletinListener()`（群公告全量；失败由 `retryGlobalBulletinListener` 每 1.5s 重试，最多 8 次）
16. `QqKernel.installTempChatForceShowHook()`（临时会话强制显示）
17. `FakeImage.loadPersistedMap()`（伪图片 md5 映射，`/sdcard/QQMax增强/tmp/.img_md5.map`）
18. `MsgListenerHook.hookSendSuffix(cl)`（发送后缀拦截）
19. `AutoTaskScheduler.start()`（定时消息，常驻线程）
20. `QzoneDynamicHook.patch(cl)` + `QqDynamicScheduler.init(cl)/start()`（QQ 动态自动点赞评论）
21. hook `Activity.onResume/onPause`（after）：`PluginEngine.setCurrentActivity(...)` 前台 Activity 追踪 + 每次 onResume 调 `retryLateInstalls()`
22. `AvatarHook.patch(cl)`（图片查看器长按保存）
23. `AddFriendVerifyHook.patch(cl)`（好友验证问答）

`retryLateInstalls()`（每次 Activity.onResume）：对 `MediaPickerHook / TranslateHook / AiSummaryHook / AvatarHook / AddFriendVerifyHook / QzoneDynamicHook` 检查 `isInstalled()`，没装上就重装（应对宿主类延迟加载）；并补注册群公告监听。

### 1.5 Config 配置系统（util/Config）

- 存储：宿主 filesDir 下 JSON 文件 `qqlite_settings.json`（800ms 节流重载 `RELOAD_MS=800`，`settingsVersion()` 供 HTTP API 判断配置变更）；另有 SharedPreferences 名 `antirecall_settings`（常量 `PREFS`，推测为旧版/状态类字段的存储）。
- 全部配置键 + 默认值（证据：Config.java 静态字段初值）：

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 防撤回总开关 |
| `mode` | 1 | 1=MODE_BLOCK 强制阻止，2=MODE_RESTORE 注入还原 |
| `send_mode` | 0 | 发送文件模式（0/1，设置页「发送文件」用） |
| `http_port` | 8081 | HTTP API 端口 |
| `translate_enabled` | true | 翻译接口替换 |
| `bulletin_all_enabled` | true | 群公告全量 |
| `force_notice_read` | "" | 已读的作者强制公告 id 集合 |
| `catgirl_enabled` | false | 自定义后缀开关 |
| `catgirl_suffix` | ""（空则用 `喵~`） | 后缀内容 |
| `system_media_picker` | true | 系统媒体选择器 |
| `native_picker` | true | QQ 原生选择器 |
| `shard_enabled` | false | 伪媒体分片自动归集 |
| `download_dir` | `/sdcard/QQMax增强/downloads` | 下载目录 |
| `plugins_enabled` | "" | 已启用插件列表（逗号分隔） |
| `auto_task_enabled` | false | 定时消息总开关 |
| `auto_task_rules` | "" | 定时规则集（多规则编码） |
| `auto_task_time` / `auto_task_content` / `auto_task_peers` / `auto_task_peer_uid` / `auto_task_chat_type`(默认2) | `08:00` / "" / "" / "" / 2 | 旧版单规则字段 |
| `auto_reply_enabled` | false | 自动回复开关 |
| `auto_reply_content` / `auto_reply_peers` / `auto_reply_at` | "" / "" / false | 自动回复内容/会话白名单/回复时艾特 |
| `keyword_reply_enabled` | false | 关键词回复开关 |
| `keyword_reply_keywords` / `keyword_reply_content` / `keyword_reply_peers` / `keyword_reply_rules` | "" | 关键词/回复/白名单/多规则 |
| `keyword_ai_url` / `keyword_ai_api_key` / `keyword_ai_model` / `keyword_ai_persona` | "" | 关键词 AI 回复（OpenAI 兼容接口） |
| `summary_url` / `summary_api_key` / `summary_model` | "" | AI 总结自定义模型 |
| `qq_dynamic_enabled` | false | QQ 动态自动任务开关 |
| `qq_dynamic_time` | `09:00` | 每日执行时间 |
| `qq_dynamic_interval` | 30 | 间隔（分钟，1–1440） |
| `qq_dynamic_like` | true | 自动点赞 |
| `qq_dynamic_comment` | false | 自动评论 |
| `qq_dynamic_comment_text` | `不错哦~` | 评论内容 |
| `qq_dynamic_whitelist` | "" | 动态白名单 |
| `qq_dynamic_like_count` / `qq_dynamic_comment_count` | 0 | 每日上限（0=不限，推测） |
| `mail_enabled` | true | QQ 邮箱功能 |
| `mail_addr` / `mail_auth_code` / `mail_name` | "" | 邮箱地址/授权码/显示名 |
| `mail_verified` | false | 授权码已验证 |
| `mail_poll_ms` | 10000 | 轮询间隔 |
| `mail_dnd` | false | 邮件通知免打扰 |
| `mail_pinned` | false | 邮箱卡片置顶 |
| `mail_unread_count` / `mail_last_uid` / `mail_inbox_cache` / `mail_web_bg` | 0 / "" / "" / "" | 邮箱状态缓存 |

### 1.6 状态上报（util/Status + StatusProvider）

- 宿主进程经 `ContentResolver.call(content://com.tencent.qqlitepro.status, "report_injected"/"get_status")` 与模块 App 通信；模块 App 的 MainActivity 显示「激活状态/注入状态/运行框架」与官方群二维码。这是跨应用 IPC，移植时不需要（补丁版直接在宿主内即可）。

---

## 2. 逐个 hook 类 / 功能清单

> 回调类型说明：before = beforeHookedMethod，after = afterHookedMethod，replace = XC_MethodReplacement / before 中 setResult 截断原逻辑。
> 「目标核对」列：宿主 M2.5 中该目标是否存在（证据来自宿主 dexdump c1–c5 与 classes4 smali）。

### 2.1 hook/SettingsInjector（设置入口与全部设置 UI，10244 行，本模块最大类）

- **功能**：把「增强模块设置」入口注入宿主设置页，并承载模块全部设置页面（自绘 View，非 Preference）；另含会话选择器、公告页、邮箱页、插件页、文件发送页等全部交互 UI。
- **hook 点**：
- `moye.wearqq.SettingsActivity.buildCategories()`（返回 List）— after：把返回值拷成新 ArrayList，追加一个用宿主类 `momoi.mod.qqpro.hook.SettingsCategory` + `kotlin.jvm.functions.Function1` 代理构造的分类项「增强模块设置 / 防撤回 / 定时消息 / 端口 / 关于」，setResult 回去。目标核对：SettingsActivity 在宿主 classes6.dex（引用见 c1/c2），SettingsCategory 见 c1/c2/c5 引用。
- 同类 `onCreate` / `attachBaseContext` — after（内部类 AnonymousClass4）：页面接管/上下文捕获；`hookHostBack(cls)` hook 返回处理方法；`hookActivityResult(cls, cl)` hook 选文件回传（插件导入/发文件用）。
- `patchSelectableText(cl)`：`android.widget.TextView.setTextIsSelectable(boolean)` 与 `TextView.setText(CharSequence, TextView.BufferType)`（findAndHookMethod，before/after）→ 强制文本可选、长按出复制弹窗（`showTextCopyDialog`）。功能文案：可选文本修复。
- 构造时还调 `NativePickerBridge.init(cl)`（见 2.10）。
- **依赖**：宿主 momoi 设置框架类（SettingsCategory/Function1/Unit）、自绘 UI 代码（本类内部，约万行）、Config、QqKernel（会话选择器拉会话列表）、QqMailManager、PluginEngine、FakeImage/FakeVoice。
- **移植难度：B**（UI 代码可整体编译进宿主 dex；注入点 buildCategories 是宿主 mod 类，直接在其方法尾追加分类项即可去掉 Xposed 层）。

### 2.2 hook/MsgListenerHook（消息监听中枢：防撤回 + 回复系 + 后缀，1171 行）

- **找监听器**：`findListener(cl)` 先按候选类名 `Class.forName`：
`com.tencent.watch.aio_impl.kernel.adapter.WatchAIOFetchService$registerMsgListener$2`（其次 `$1`、`$0`、`com.tencent.qqlite.aio_impl...$2`、`com.tencent.app.aio_impl...$2`）；都失败则用 `XposedBridge.getLoadedClasses()` 模糊扫描含 `WatchAIOFetchService`+`registerMsgListener` 的已加载类。目标核对：`WatchAIOFetchService$registerMsgListener$2` 定义在宿主（c 系列 dump 可见其内部类 `$onRecvMsg$1` 等）。
- **hook(Class)**：遍历该监听器类全部 declared methods 按名分发（XposedBridge.hookMethod，全为 **before**）：
- `onRecvMsg` / `onMsgInfoListAdd` / `onMsgInfoListUpdate` → `callbackCacheList(param,0)` + `maybeReplyAll(param,0)`
- `onAddSendMsg` → `callbackAddSendMsg`
- 方法名含 `Delete` 或 `Recall`（且不以 is/get 开头，`isRecallSignal`）→ `callbackOnMsgDelete` + `applyBlockMode`
- **hookGlobal(cl)**：同上逻辑作用于 `com.tencent.qqnt.msg.MsgService$init$1`（内核全局消息回调，区别是删除信号只认 `onMsgDelete`）。目标核对：该类见 c4 dump。
- **hookSendSuffix(cl)**：`com.tencent.qqnt.msg.api.impl.MsgServiceImpl` 中所有名为 `sendMsg` 且参数含 `java.util.ArrayList` 的重载 — before：汇总参数中 MsgElement 列表的 `textElement.content`，调 `applyCatgirlSuffix`。
- **防撤回核心逻辑**（与 §5.1 详述）：
- `callbackCacheList`：`antiRecallEnabled()`（= `Config.enabled()`，异常时默认 true）才处理；逐条消息：若 `MsgCache.isRecallRecord(msg)`（含 revokeElement）→ mode=1 时 `MsgCache.restoreInto(msg)` 还原原文（日志「本次还原被撤回消息原文 N 条」），mode=2 时 `MsgCache.feedTip(msgId)`（日志「本次向灰条投喂原文」）；否则 `doCache` 缓存 + `dumpRaw`（Gson 序列化进 RawMsgStore）+ `collectFakeParts`（伪媒体分片归集）。
- `callbackOnMsgDelete`：从参数中解析 msgId 列表，跳过自身消息（`isSelfMessage`），`MsgCache.markRecalled(msgId)` 后调 `UiPatcher.onMessagesRecalled()` 触发重渲染。
- `applyBlockMode`：mode=1 且目标方法返回 void 时 before 里 `setResult(null)` 直接吞掉删除回调（日志「强制阻止撤回：拦截 onMsgDelete，消息保留（红边待渲染）」）——**replace 型**。
- `callbackAddSendMsg`：缓存自己发出/同步的消息（并记录 `sSelfUid`、发送去重表 `sSentIds`）。
- **自动回复系**（`maybeReplyAll`，在 onRecvMsg 等回调里同步触发，内部开线程发送）：
- 自动回复：`auto_reply_enabled` + 会话白名单 `auto_reply_peers` 命中 → 回 `auto_reply_content`，`auto_reply_at` 时带艾特；冷却/去重常量 `REPLY_COOLDOWN_MS`、`REPLY_DEDUP_MS`、`SELF_DEDUP_MS`。
- 关键词回复：`keyword_reply_enabled`；规则支持单条字段与多规则 `keyword_reply_rules`（`|` 分隔编码，代码 `split("\\|", 3)` / `split("\\|")`）；命中后可回复并艾特（日志「(回复并艾特)」），艾特目标解析用元素字段 `atElement/atNtUid/atTinyId/atUid/atUidList` 与哨兵 `SELF_AT_SENTINEL = "\u0000@self\u0000"`、标记串 `ꡦATꡦ`。
- 关键词 AI 回复：`handleKeywordAi` → `AiSummaryHook.keywordReply(...)` 用 `keyword_ai_url/api_key/model/persona` 调 OpenAI 兼容接口生成回复；同用户冷却 `AI_REPLY_COOLDOWN_MS`，线程名 `keyword-ai-reply`。
- 插件分发：`dispatchToPlugins` → `PluginEngine.dispatchIncomingMessage(json)`（json 含 msgId/peerUid/chatType/senderUid/senderUin/senderNick/text）。
- 发送通道统一走 `AutoTaskScheduler.sendElements(ctx, chatType, peerUid, QqKernel.buildElementsWithAt(...))`（内核反射发送，见 2.16）。
- **自定义后缀**：`applyCatgirlSuffix`：`catgirl_enabled` 时把 `Config.catgirlSuffix()`（默认 `喵~`）追加到最后一个 textElement 的 `content` 字段（`XposedHelpers.setObjectField`），日志「自定义后缀: 已在发送消息末尾追加」。
- **依赖**：MsgCache/ElementReplicator/RawMsgStore、UiPatcher、QqKernel、AiSummaryHook、PluginEngine、Gson。消息对象全靠字段反射读（`msgId/peerUid/chatType/senderUid/senderUin/senderNick/sendUin/elements/textElement/picElement/pttElement/fileElement/md5HexStr/sourcePath/filePath/thumbPath`）。
- **移植难度：B**（监听器类是宿主 dex 内的 Kotlin 协程类，其方法体可直接改：缓存/回复逻辑需把模块代码编译成新类合并进宿主 dex 并在方法入口插入调用；applyBlockMode 的「吞掉 onMsgDelete」本身是 A 级小改）。

### 2.3 hook/UiPatcher（防撤回渲染 + 长按菜单 + 伪媒体，3446 行）

子安装函数与目标（全部按「类名 + 方法名 + 参数特征」反射匹配后 hookMethod）：

| 安装函数 | 目标（宿主类#方法） | 回调 | 效果 |
|---|---|---|---|
| `patch()` | —（无 hook） | — | 启动样式对账线程 `antirecall-style-reconcile`（`startStyleReconcile`），周期性修正已渲染 cell 的角标/红边 |
| `patchBaseCell` | `com.tencent.watch.aio_impl.ui.cell.base.BaseWatchItemCell#i(View,...)`（参数 ≥2 且首参 View） | after（baseCellBindHook） | 消息气泡绑定后按 MsgCache 状态打标：红边框 + 角标「」(RED)、「已编辑」(ORANGE)、伪图/伪语音角标（常量 BADGE_EDITED/BADGE_FAKEIMAGE/BADGE_FAKEVOICE） |
| 同上 | `com.tencent.watch.aio_impl.ui.cell.pic.WatchPicItemCell#d(View,...)` | after（picCellBindHook） | 图片消息绑定：记录 ROOT_MSGID，`detectFake` 识别伪图 → 打伪图角标 + 调度分片归集；否则 `ensureReceiverMedia` 准备接收端原文件下载 |
| `patchMsgFactory` | `com.tencent.watch.aio_impl.ext.MsgListUtilKt#c_0(MsgRecord)`（单参且参数类型为 MsgRecord） | before | mode=1 且记录是撤回记录时 `restoreInto` 还原，使其「渲染为原气泡」（日志「c_0 界面记录还原成功 -> 渲染为原气泡」）。目标核对：宿主签名 `c_0(Lcom/tencent/qqnt/kernel/nativeinterface/MsgRecord;)Lcom/tencent/watch/aio_impl/data/WatchAIOMsgItem;` ✔ |
| `patchGrayTipCell` | `com.tencent.watch.aio_impl.ui.cell.graytips.WatchGrayTipsCell#d(...)`（参数 ≥2 的全部同名方法） | after（grayTipBindHook） | 灰条（撤回提示）绑定后 `styleRecalledTip`：mode=2 时把 MsgCache 里存的原文注入灰条显示；非撤回灰条 `resetGrayTipsStyle` 复位 |
| `patchLongPressMenu` | `momoi.mod.qqpro.hook.style.LongPressMenu#build(...)`（参数 ≥4 且返回 View） | after（longPressMenuBuildHook，内部类 $8） | 在宿主 mod 的消息长按菜单里注入行：**编辑**（本地改显示文本，存 EDITED_TEXT 并打「已编辑」标；条件：有文本元素、未撤回、非自己消息）、**伪图片/伪语音下载还原行**、**文件详情/文件原始信息行**（文件名/MD5/大小/格式/上传时间弹窗）。目标核对：宿主签名 `build(LAIOLongClickMenuFragment;LayoutInflater;MsgRecord;WatchAIOMsgItem;FragmentManager;Z)Landroid/view/View;`（classes4.dex）✔，hook 里 args[0]=fragment、args[2]=MsgRecord、args[3]=item（取其字段 `d`）与之吻合 |
| `patchPttWaveView` | `com.tencent.watch.aio_impl.ui.cell.ptt.PttAudioWaveView#f(List,...)`（首参 List 的同名方法） | before（pttWaveSetDataHook） | 伪语音波形防崩：把参数 List 中的 Number 元素统一转成 Byte 再传给原方法，避免类型异常崩溃 |

- 其它内部能力：`detectFake/detectFakeFile`（按 picElement/pttElement 的 md5、路径、尺寸等判定伪媒体，带缓存与负缓存 TTL `FAKE_DETECT_NEGATIVE_TTL_MS`）、`collectRealFileInfo`、`scheduleShardCollect`（分片归集调度，线程 `SHARD_COLLECT_EXEC`）、`onMessagesRecalled()`（主线程重刷已绑定 cell）。
- **依赖**：MsgCache、FakeImage/FakeVoice、FakeMediaReceiver、Config（mode/enabled/shardEnabled）。
- **移植难度：B**（每个 after/before 逻辑都可改写为目标方法体内联代码；目标全在宿主 dex 内，其中 LongPressMenu/MsgListUtilKt 与 bigface 同在 classes4.dex）。

### 2.4 hook/FakeMediaReceiver（伪媒体接收端原文件下载，309 行，无 Xposed hook）

- **功能**：UiPatcher 识别出伪图/伪语音后，由它向内核请求原文件下载：反射构造 `com.tencent.qqnt.kernel.nativeinterface.RichMediaElementGetReq`，经 `com.tencent.qqnt.msg.KernelServiceUtil` 取得服务后调 `getRichMediaElement`，并用 `defpackage.WatchPicElementExtKt`（字符串 `assembleMobileQQRichMediaFilePath`、`importRichMediaContext`）拼本地路径（picOriginalPath/plusPicThumbPaths/pttPath）。日志「[伪媒体] 已下发接收端原文件下载 elementId=」。
- **依赖**：纯反射内核 API；由 `UiPatcher.patchBaseCell` 开头 `FakeMediaReceiver.init(cl)` 初始化。
- **移植难度：B**。

### 2.5 hook/ChatCardInjector（QQ 邮箱会话卡片，1682 行）

- **功能**：在会话列表顶部注入一张「QQ 邮箱」会话卡片（头像/未读角标 QUIBadge/最新邮件摘要/时间），点击进模块邮箱页，长按弹宿主原生会话菜单（置顶/免打扰/删除项复用宿主 `com.tencent.qqnt.watch.chat.list.longclick.SetTopItem/SetDisturbItem/DeleteItem`），其中邮箱卡片的菜单动作被拦截改走模块逻辑。文案证据：「ChatCard 已 hook RecyclerView.setAdapter 注入邮箱会话卡片」。
- **hook 点**：
- `androidx.recyclerview.widget.RecyclerView#setAdapter(Adapter)` — before/after：识别会话列表 adapter 后包装注入。
- 对 adapter 类及其父类用 `hookAll/hookInHierarchy/hookItemBySignature` 批量 hook `getItemCount/onBindViewHolder/getItemViewType` 等（hookAllMethods/hookMethod 混用）做位置偏移与卡片绑定；卡片 holder 优先用宿主 `com.tencent.qqnt.chats.core.adapter.holder.HeaderItemHolder` + `ChatViewBuildUtil` 构建（`buildCardFromHost`），失败回退自绘卡（`buildCardFallback`）。
- `refreshChatList()` 供 MainHook 晚注入补齐时调用。
- **门控**：`Config.mailEnabled`（默认 true）决定卡片是否显示；未读数读 `Config.mailUnreadCount`；卡片头像用一张百度图床 URL 常量（`https://img2.baidu.com/it/u=3554844775,...`，推测为邮箱图标占位）。
- **依赖**：QqMailManager（未读/摘要）、SettingsInjector（点击打开邮箱页）。
- **移植难度：B**。

### 2.6 hook/MediaPickerHook（系统媒体选择器 + 添加好友入口，1233 行）

- **功能**：
1. 接管聊天「+」面板的「相册」单元格：点击后拉起**系统媒体选择器**（`ACTION_GET_CONTENT`/`ACTION_OPEN_DOCUMENT` 拼 Chooser「选择应用」，支持多选 ALLOW_MULTIPLE），选中的图片/视频复制到私有目录后，优先写回宿主内联输入框（构造 `momoi.mod.qqpro.lib.ImageTag` 令牌经 `moye.wearqq.IMEOperation.extraMsg` + `openIME` 插入预览），失败则用内核直接发送 MsgElement。
2. 在聊天「+」面板注入模块设置入口（tag `qqlitepro_panel_settings_entry`）。
3. 在陌生人/临时会话的聊天设置页注入「添加好友」入口，点击经内核发好友申请（日志「添加好友: 发起申请 uid=」）。
- **hook 点**：
- `com.tencent.watch.aio_impl.ui.frames.MenuFrame#onViewCreated(View, Bundle)` — after：接管相册单元格（`takeover`）、挂 RecyclerView 子项 attach 监听（`attachCells`，适配器重绑后重新接管，另 hook 适配器 `onBindViewHolder`）。
- `com.tencent.watch.aio_impl.ui.WatchAIOFragment#onActivityResult` 系列（`hookResult`，findAndHookMethod）— before/after 截获选择器回传（`handleResult`，请求码 `REQ_MEDIA_PICK`，线程 `antirecall-media-pick/antirecall-media-send`）。
- `com.tencent.watch.aio_impl.ui.frames.SettingFrame#onViewCreated(View, Bundle)` — after：`injectAddFriendEntry`。
- 当前会话参数经 `momoi.mod.qqpro.hook.action.CurrentContactKt`（getCurrentContact）与 Fragment arguments（`key_bundle_chat_type`/`key_bundle_peer_id`）读取；发送走 `momoi.mod.qqpro.hook.InlineInput` 就绪状态 + 内核 MsgElement 构造（`com.tencent.qqnt.kernel.nativeinterface.MsgElement`）。
- **门控**：`Config.systemMediaPickerEnabled(ctx)` 在接管点与回传点各查一次（默认 true）；选择器模式（系统/QQ原生/自建弹层）由基础设置页三档切换（见 §3）。
- **移植难度：B**。

### 2.7 hook/NativePickerBridge（QQ 原生选择器桥接，993 行）

- **功能**：不用系统选择器时，把宿主原生的选人界面（好友/群成员选择 Fragment）以编程式导航方式拉起并回传结果，供设置页的会话选择器、转发选人等流程用；同时修复原生选择器的群列表/群成员数据与预选、上限问题。
- **hook 点**（init 由 SettingsInjector.patch 调用）：
- 某 Compat Activity（常量 `COMPAT_ACT`）`onCreate(Bundle)` — findAndHookMethod。
- 好友选择 Fragment（`FRIEND_SELECT_FRAG`）`onCreate` 全部重载、`Y`、`b0` — hookAllMethods（`onFriendCreate/onCompatCreate/onSubmitList`）；另扫其 declared methods 做提交拦截（`sHookedSubmit`）。
- 群成员选择 Fragment（`GROUP_MEMBER_FRAG`）`Y`、来源群 Fragment（`FROM_GROUP_FRAG`）`Y` — hookAllMethods（`onGroupMemberFix/onGroupListFix`）。
- `Activity#finish/onResume/onDestroy` — 生命周期兜底（等 resume 后确认结果 `awaitResume/consumeResultAndConfirm`）。
- `com.tencent.qqnt.watch.ui.componet.tips.TipsUtils#g` 与 `#b` — hookAllMethods（钳制提示/上限，配合常量 `KEY_MAX_SIZE/CAN_CONFIRM_SIZE/FORCE_SELECT_LIST`）。
- 导航构造：反射 `NavHost/NavGraph/Navigator/FragmentDestination`（常量 NAV_HOST_CLS 等）自建导航图跳到目标 Fragment，并注册 FragmentResult 监听（`RESULT_LISTENER_CLS`）拿选择结果；资源 id 经 `com.tencent.qqlite.R$id` 反射取。
- **门控**：`native_picker`（默认 true）决定选人流程走原生桥接还是模块自建弹层。
- **移植难度：B**（逻辑重但自成一体；目标 Fragment 类名需在移植时逐一对 M2.5 核名，个别混淆名 `Y/b0` 已由模块写死）。

### 2.8 hook/AddFriendVerifyHook（好友验证问答，282 行）

- **功能**：对方设置了验证问答时，手表原生流程无法答题；模块拦截提交点弹自绘对话框（标题「好友验证」，显示问题 `question`、输入答案、发送/取消），填完后放行原生流程发送。
- **hook 点**：`com.tencent.qqnt.watch.add.result.QQSearchFriendFragment$onViewCreated$1$2$invokeSuspend$$inlined$collect$1#emit(...)`（findClassIfExists + 扫方法名 `emit`，hookMethod）— **before**：读参数中的验证信息（字段 `addFriendSetting/question/buddyUin`），`handleEmit` 弹窗；用户提交后记录答案并 `setResult` 放行/或交由原生流程（字符串「, 交由原生流程发送」）。
- **移植难度：B**（目标是宿主协程生成类，方法体可改；弹窗代码内联）。

### 2.9 hook/AvatarHook（图片查看器长按保存，326 行）

- **功能**：在宿主 mod 的图片浏览器里长按图片 → 弹「保存图片 / 下载并保存 / 用缓存图保存」，保存到 `/sdcard/Pictures/QQ Max`（常量 SAVE_DIR，文件名前缀 `Image_`）。
- **hook 点**：
- `momoi.mod.qqpro.hook.view.MediaPager$MediaAdapter#onBindViewHolder(holder, int)`（参数个数=2 的重载，findAndHookMethod）— after：给 itemView 挂长按监听（内部类 $2），长按时经 adapter 反射取图（`getImageUrl/getFullUrl/getImageLocalPath/getImage`、字段 `items`）弹保存框（`showSaveDialog/downloadAndSave/saveFromPath`）。
- `momoi.mod.qqpro.hook.QZoneMediaViewer#build(QZoneMediaBrowserFragment, LayoutInflater)` — after：找到结果 View 里的 ViewPager2 及其 adapter 并登记到 `sQzoneMediaAdapters`，使空间媒体浏览场景**不**挂上面的保存长按（日志「动态媒体长按已屏蔽」；推测：避免与空间浏览自身手势冲突）。
- **移植难度：A**（onBindViewHolder 末尾加一段设长按监听的代码即可等效；QZoneMediaViewer 的屏蔽登记同理）。

### 2.10 hook/TranslateHook（翻译接口替换，319 行）

- **功能**：把 QQMax 内置翻译换成自定义网关：优先 `https://api.corexwear.com/fy2/index.php?msg=`（常量 API_BASE，「自定义网关」），失败回退有道网页版 `https://m.youdao.com/translate?type=AUTO&inputtext=`（YDAO_URL，解析 HTML `<li>`）；异步线程 `qqlite-translate`，结果经原回调对象反射 `invoke` 回填。
- **hook 点**：`momoi.mod.qqpro.hook.translate.Translator#translate(...)`（参数个数=3 的重载）— **before/replace**：`Config.translateEnabled`（默认 true）时取 args[0]=原文、args[2]=回调，`setResult(null)` 后 `runTranslateAsync`。
- **⚠ 目标核对失败**：宿主 M2.5 的全部 dex 中**不存在** `momoi.mod.qqpro.hook.translate.Translator` 类，只有 `HistoryTranslate`（register/toggle/has/isOn）、`MessageTranslate`、`TranslateAll`（证据：宿主 Class descriptor 全量检索）。因此该功能在 M2.5 上实际**从未装上**（日志「未找到 Translator.translate 目标方法（翻译替换未生效，稍后重试）」，靠 retryLateInstalls 反复重试）。移植时需先定位 M2.5 真实翻译入口（推测在 MessageTranslate/HistoryTranslate 一带），不能照搬。
- **移植难度：B**（网关调用代码可内联，但入口要重新定位）。

### 2.11 hook/AiSummaryHook（AI 总结自定义模型 + 关键词 AI，707 行）

- **功能**：接管宿主 mod 的原生总结 API：配置了 `summary_url` 时，把聊天记录按风格组 prompt 发到 OpenAI 兼容接口（Bearer `summary_api_key`，模型 `summary_model`，SSE 流式 `data:` 解析），结果流式回调宿主 Listener；未配置则放行宿主原生总结（日志「未配置自定义模型地址，沿用 QQMax 原生总结」）。风格三档：`tldr` 简短摘要 / `detailed` 详细 / `bullets` 要点（原文 prompt 在类内）。另对外提供 `keywordReply(...)` 给关键词 AI 回复复用。总结历史落盘 `ai_summary_history.txt`（上限 HISTORY_MAX）。
- **hook 点**：`momoi.mod.qqpro.hook.summarize.Summarizer#summarize(List, int, String, Summarizer$Listener)`（先精确签名匹配，失败按参数个数=4 回退）— **before/replace**：命中后 `setResult(null)`，`runCustomSummaryAsync`（线程 `antirecall-summarize`）组装消息文本（反射读元素 `getText/getSender`）→ HTTP POST → 回调 Listener 的 `onChunk/onDone/onError`。
- **目标核对**：宿主签名 `summarize(Ljava/util/List;ILjava/lang/String;Lmomoi/mod/qqpro/hook/summarize/Summarizer$Listener;)V`（classes.dex）✔ 与模块精确匹配一致。
- **依赖**：网络（用户自填的 API 地址）、文件（历史）。
- **移植难度：B**（入口是宿主 mod 类的方法，直接改其方法体或在方法头插入转发都可；HTTP/SSE/prompt 代码需整体搬入）。

### 2.12 hook/QzoneDynamicHook + auto/QqDynamicScheduler + http/QqDynamic（QQ 动态定时点赞/评论）

- **功能**：每天定时（`qq_dynamic_time` 默认 09:00，或按 `qq_dynamic_interval` 分钟间隔）自动给好友动态点赞/评论：白名单 `qq_dynamic_whitelist` 过滤，评论内容 `qq_dynamic_comment_text`（默认 `不错哦~`），点赞/评论各自开关与每日数量上限。
- **hook 点（数据捕获，全 after）**：
- `com.tencent.watch.qzone_impl.feed.QZoneFeedService#b(List, boolean, boolean, boolean, int)` — after → `QqDynamicScheduler.onNativeFeedList(list)`。
- `com.tencent.watch.qzone_impl.frame.QZoneMainFrame#O_0(List, boolean)`（失败回退 `#O`）、`QZoneMineFragment` 同名方法 — after → `onUiFeedList`。
- `com.tencent.watch.qzone_impl.frame.QZoneFeedAdapter#onBindViewHolder(ViewHolder, int)` 与 `(ViewHolder, int, List)` — after → 逐条 dispatch 捕获 Feed。
- **执行通道两条**：
1. 宿主 mod：反射 `momoi.mod.qqpro.hook.qzone.QzoneActions.INSTANCE.toggleLike(...)` 点赞；`new momoi.mod.qqpro.hook.qzone.QzoneCommentThread(...).postComment(...)` 评论；取 FeedService 单例走 `QZoneFeedService.h()` + `ServiceCallbackWrapper` + 动态代理 `IAdapterHost` 主动拉取列表。
2. Web API（http/QqDynamic）：用 QqKernel 取的 QZone 票据（`qzoneSkey/qzonePskey/qzonePt4Token`，`g_tk=bkn(skey)`）直接调 `emotion_cgi_msglist_v6`/`feeds3_html_more` 拉今日动态、`qzone_like` 点赞、`add_for_comment` 评论（Cookie 由票据拼装）。
- **依赖**：网络（qzone.qq.com）、宿主 mod Qzone 类、内核票据反射。
- **移植难度：B**。

### 2.13 hook/ForceNoticeInjector（作者强制公告，921 行）— 不建议移植

- **功能**：拉取作者远程配置 `https://devfile.vip/preview/5bbpqi7q/config.json`（含多节点 `configs` 列表与公告条目 id/title/content），在宿主 Activity 前台时**强制弹公告**，未读完不许返回（`force_notice_read` 记录已读）。
- **hook 点**：`android.app.Activity#onResume/onStart/onBackPressed`（findAndHookMethod，after/before 组合）+ 经 ActivityThread 反射枚举 `mActivities` 找当前 Activity 弹窗；拉取线程 `force-notice-fetch/force-notice-refetch`。
- **依赖**：作者服务器（第三方域名 devfile.vip）、网络。
- **移植难度：C**（依赖作者远程服务；且属作者推送通道，移植无意义）。

### 2.14 hook/RiskControlBypassHook（风控绕过，570 行）— 高风险，移植需慎重

两个入口：`patch(param)`（主进程）与 `patchMsfProcess(param)`（MSF 进程），内容大体一致：

- **Root/环境检测绕过**（全为框架/系统类 hook）：
- `java.io.File#exists()` — before：路径含 `/su`、`/proc/` 等敏感项时改结果（字符串含 `/su`、`/proc/`、`/status`）。
- `java.lang.Runtime#exec(String, String[], File)` — before 拦截检测命令。
- `android.os.SystemProperties#get(String)` / `get(String, String)` — before 改返回值（伪造属性）。
- `android.os.Debug#isDebuggerConnected()` / `waitingForDebugger()` — **XC_MethodReplacement.returnConstant(false)**。
- `java.io.FileInputStream#read` 全部重载（hookAllMethods）— after 过滤内容：把 `/proc/self/status` 里的 `TracerPid:\s*\d+` 替换为 `TracerPid:\t0`（正则原文），抹除 maps 中的可疑行（推测）。
- **图灵盾（Turing FD）伪造**：反射 `com.tencent.turingfd.sdk.xq.PhoneResp` 造动态代理应答；伪造 Build 指纹常量：`FINGERPRINT="Android/wt/watch:13/TQ3A.230805.001/1234567:user/release-keys"`，以及 BOARD/BOOTLOADER/BRAND/DEVICE/HARDWARE/MANUFACTURER/MODEL/PRODUCT/TAGS 等字段值（`Tencent`/`Watch`）；日志「[风控] 图灵风控已关闭」。
- **灰度角色检查拦截**：hook `com.tencent.mobileqq.msf.core.auth.b` 与 `com.tencent.mobileqq.msf.core.r` 的相关方法（hookMethod 多处），拦截 `GrayUin/GrayUinCheckResp/FromServiceMsg`（日志「拦截灰度角色检查(上报 PROCESS_QQ)」）。
- **服务器踢线拦截（防顶号下线）**：hook `com.tencent.qqnt.kernel.api.impl.KickApiImpl$setupKickListen$listener$1#onKickedOffLine` 与 `com.tencent.qqnt.account.kick.api.impl.KickProcessorApiImpl$WatchKickProcessor` 的处理方法（`buildKickHook`）— before：参数中找 `KickedInfo`，读 `kickedType`/`securityKickedType`/`tipsTitle`；若 kickedType 不在账号类白名单 `ACCOUNT_KICK_TYPES`（含 `KKICKBYPASSWORDCHANGE`、`KKCIKBYLOWVERSION` 等）且 securityKickedType≠0（即风控类下线）→ `setResult(null)` 吞掉（日志「-> 拦截(风控检测下线)」，正常下线则「放行(正常下线)」）。另有日志「[踢线] setAccountNoLogin(msf) 执行 uin=」。
- **移植难度：C**（hook 的是框架类与 MSF 独立进程，宿主 smali 无法等效全局拦截；且属反检测功能，移植后可能触发更严风控——仅记录，不建议优先）。

### 2.15 hook/MsfKeepAliveHook（MSF 推送保活，308 行）

- **功能**：让手表 QQ 的消息推送在后台不被系统/MSF 自身省电策略杀掉：推送到达时临时把 MSF 拉前台（真实前台服务 + WakeLock 5 分钟 + 常驻通知「QQMax 消息推送」），处理完恢复省电。
- **hook 点**：
- MSF 进程：`com.tencent.mobileqq.msf.service.MsfService#onStartCommand`（日志「已 hook MsfService.onStartCommand（按需临时前台）」）；`com.tencent.mobileqq.msf.service.i#d()` / `#g()`（强制启用 JobScheduler 保活开关、拦截保活任务取消，日志「已强制启用 MSF JobScheduler 保活开关」「已拦截 MSF 保活任务的取消」）。
- 主进程：`com.tencent.qqnt.manufacturer.func.ManufacturerRouter#a` 与 `IThirdPushApi`（`registerThirdPush/getPushType/getIsServiceExist`）— 接管三方推送注册（日志「已接管 IThirdPushApi（真实前台服务/WakeLock）」），经 `MobileQQ`/`ActivityThread.currentApplication` 取上下文下发前台控制（WakeLock tag `qqlitepro:push`，通知渠道 `qqlitepro_fg`）。
- **移植难度：C**（跨 MSF 独立进程 + 前台服务/通知组件涉及系统行为与 manifest，宿主内改 smali 只能覆盖主进程侧一小部分）。

### 2.16 http/QqKernel（内核反射桥，6698 行，全模块地基，不是 hook 类）

不 hook 任何方法（`installTempChatForceShowHook` 除外），而是把 NT 内核能力反射成静态 API 供全模块调用。取服务路径：`com.tencent.qqnt.msg.KernelServiceUtil.g()` → `IQQNTWrapperSession` → `getMsgService()/getGroupService()` 等。能力清单（方法名即证据）：

- 元素构造：`createContact/createTextElement/createFaceElement/createAtElement/createAtAllElement/buildElementsWithAt/createPttElement/createPicElement/setPicDisplaySize/createReplyElement/createVideoElement`
- 发送：`sendMsg(session, elements, timeout)`、`sendReplyMsg(...)`（结果类 SendResult{code,msg,timeout}）
- 撤回：`recallMsg(peer, msgId, timeout)` → `IKernelMsgService.recallMsg(Contact, ArrayList<Long>, IOperateCallback)`
- 会话/自我：`getSessions/selfInfoJson/selfUid/selfUin/isSelfTarget/isSelf`
- QZone 票据：`qzoneSkey/qzonePskey/qzonePt4Token/qzoneBkn/qzoneHttpGet/qzoneHttpPost/qzoneSelfUin`
- 好友请求：`approveFriendRequest/buddyReqInfo/buddySetting`、群请求处理（供 HTTP API）
- **群管理全套（见 §5.3）**：`groupDetail/groupCodeOf/groupInfoJson/isGroupOwnerOrAdmin/getGroupMembers/banAllStatus/setGroupShutUp/modifyGroupName/modifyGroupRemark/kickMember/quitGroup`
- **群公告**：`fetchGroupNotices(groupCode)`、`registerGlobalBulletinListener()`——经宿主 mod `momoi.mod.qqpro.api.GroupBulletinApi` 注册 Listener（回调 `onGetGroupBulletinListResult/onGroupBulletinChange/onGroupBulletinRemindNotify`），并直接调内核 `IKernelGroupService.getGroupBulletinList/getGroupBulletin` 拉全量（日志「去除数量限制: 内核 getGroupBulletinList 取回全量公告 gc=」）；公告图片走 `downloadGroupBulletinRichMedia` 接管下载（AnonymousClass5，before hook 下载回调类方法，细节 jadx 未能反编译该方法体，此处据字符串与相邻代码归纳）。门控 `bulletin_all_enabled`（默认 true）。
- **官方群门禁查询**：`queryOfficialGroupState(timeoutMs)`（见 §1.3）、`isGroupPassed` 辅助。
- **临时会话强制显示** `installTempChatForceShowHook()`：一组 hook 让陌生人临时会话出现在会话列表并可正常发消息：hook 消息回调/数据源捕获/渲染关口（`dedupTempChatRenderArgs/patchTempChatRenderArgs/scanTempChatRecords/forceShowTempChat`）、好友校验放行、以及 `MsgServiceImpl` 中 `sendMsg(long, Contact,...)`（5 参、首参 long、次参 Contact 的重载）before 把临时会话的 chatType 由 1 改 100 放行（日志「临时会话: 发送放行 peerUid=... chatType 1->100」）；已知临时会话落盘在 SharedPreferences（常量 TEMP_CHAT_PREF）。
- **移植难度：B**（纯反射调用在宿主进程内可原样成立，代码可整体搬入；其中 installTempChatForceShowHook 的多点 hook 逻辑需逐个改写为宿主方法体内联，工作量大）。

### 2.17 http/HttpApiServer + http/AdminWeb（HTTP API 与管理后台，1514 + 20 行）

- **功能**：在宿主进程内开一个无鉴权的 HTTP 服务（`new ServerSocket(Config.port(ctx), 50)`，默认 8081，**绑定全部网卡且无 token/密码校验**——安全提示：同局域网任何人可调用发消息/改配置接口），供浏览器/脚本遥控 QQ。
- **路由**（证据：handleRequest 分发链）：
- `GET /get_sessions_info`、`GET /get_recent_msgs`、`GET /get_msg_history`
- `POST /send_msg`、`/send_reply_msg`、`/send_image`、`/send_video`
- `POST /set_whitelist`（自动回复白名单）、`POST /ban_all_status`（查全员禁言，实际调 QqKernel.banAllStatus/setGroupShutUp 一族，推测同端点兼写）
- `POST /get_group_notices`
- `GET /get_friend_requests`、`POST /send_friend_request`、`POST /handle_friend_request`
- `GET /get_group_requests`、`POST /send_group_request`、`POST /handle_group_request`
- `GET /` 或 `/admin` → AdminWeb 管理后台单页（标题「QQMax增强模块 · 管理后台」，含配置编辑/会话/统计界面）
- `GET|POST /api/config`（读/写全部 Config）、`GET /api/version`、`GET /api/sessions`、`GET /api/stats`、`POST /api/list_files`（列下载目录文件）
- `GET /mail_html`、`/mail_file/<id>/<name>`（邮件 HTML 正文与附件，经此服务渲染/下载，附件目录 filesDir/mail_att）
- **依赖**：QqKernel、MsgCache/RawMsgStore、FakeImage/FakeVoice、QqMailManager、SettingsInjector（wrapHtmlWithCss/mailBgDataUrl）、Gson。
- **移植难度：B**（纯 Java socket 服务，可整体搬入宿主进程；但建议移植时加本机回环绑定或 token）。

### 2.18 http/FakeImage + http/FakeVoice（伪媒体文件传输，933 + 932 行）

- **功能**：把任意文件伪装成图片/语音消息发送、并在接收端还原——绕过手表 QQ 不能直接发文件的限制：
- 伪图片：文件字节封装进 PNG 容器（前缀标记 `qqlpi_`，分片 `qqlpis_`；元信息含原文件名/大小/MD5，以 `|` 分隔编码），大文件自动分片（`.part`），每片作为一张图片发出；持久化映射 `/sdcard/QQMax增强/tmp/.img_md5.map`、`.shard_paths_img.map`。
- 伪语音：同理封装为语音文件（`wrapToFakeVoice`）。
- 接收端：UiPatcher/MsgListenerHook 识别（`isFakeImage/isShard/isFakeVoice`）→ `collectPart/autoCollectAllParts` 把分片归集到 `/sdcard/QQMax增强/downloads/.parts` → `mergeParts` 合并 + MD5 校验（日志「伪图片还原MD5不一致: want=... got=」）→ 还原到下载目录。
- **入口**：设置页「发送文件」（buildFileSend，配合 `send_mode` 与 `shard_enabled`）与「URL 转语音发送」（buildVoiceUrl：下载音频后按伪语音发送）。
- **移植难度：B**（纯文件处理 + 元素构造，无 Xposed 专属依赖）。

### 2.19 mail/QqMailManager（QQ 邮箱，1555 行）

- **功能**：完整的 QQ 邮箱客户端：JavaMail IMAPS 收信（`imap.qq.com:993`，授权码登录 `verify`）、SMTP 发信（`smtp.qq.com`，支持附件，上限 `MAX_ATT_BYTES=12MB`）、收件箱缓存、未读计数、新邮件系统通知（`notifyNewMail/showSystemNotification`，受 `mail_dnd` 控制）。
- **保活**：`ensurePolling` 在 `mail_verified` 后启动；IMAP IDLE 保活（`ensureIdleAlive/isIdleHealthy`，IDLE 正常时轮询 60s，异常回退 `mail_poll_ms` 默认 10s 轮询）；`pollInbox` 拉取并写 `mail_inbox_cache/mail_last_uid/mail_unread_count`。
- **呈现**：正文 HTML 落盘后经 HttpApiServer `/mail_html` 渲染（可自定义背景 `mail_web_bg`）；列表/详情/写信 UI 在 SettingsInjector 的邮箱页（16–22 号页）；会话列表卡片由 ChatCardInjector 呈现。
- **移植难度：B**（JavaMail 库与代码可整体搬入；需把 javax/mail 等库 dex 合并进宿主包）。

### 2.20 auto/AutoTaskScheduler（定时消息，264 行）

- **功能**：常驻守护线程（30 秒一轮 `Thread.sleep(30000L)`）检查时间：多规则 `auto_task_rules`（Rule{time, peers, content, replyAt}，规则编码 `escapeField/unescapeField`）到点后逐会话发送，并以 `auto_task_last_sent_day` 防同日重复；兼容旧版单规则（`auto_task_time/content/peers`）。发送走 `send/sendElements` → QqKernel 元素构造 + 内核发送（可带艾特）。
- **移植难度：B**。

### 2.21 plugin/PluginEngine + PluginAPI + ConsoleBridge（JS 插件系统，778 + 1206 + 36 行）

- **功能**：从宿主 `filesDir/qqlite_plugins/*.js` 加载 Rhino JS 插件（`ensureLoaded/reload`），插件可注册「Feature」（开关存 Config 的插件设置区，启停名单 `plugins_enabled`），在设置页「插件管理」导入/删除/启停/看日志；官方公告页还能下载插件包导入（SettingsInjector.downloadPluginForNotice/confirmImportPlugin）。
- **PluginAPI 暴露给 JS 的能力**（方法名即证据）：`log/toast/registerFeature/getSetting/setSetting/hook(className, methodName, callbackName)`（通用 Xposed hook）、`onMessage(callback)`（入站消息回调，由 MsgListenerHook.dispatchToPlugins 分发）、`sendText/sendVoice/sendImage/sendFile/sendFace/sendVideo/sendReplyMsg`、`banAll/banAllStatus/isGroupAdmin`、`kickMember/quitGroup/modifyGroupName/modifyGroupRemark/getGroupMembers/getGroupNotices/getGroupInfo/getGroupCode`、`getSessions/getSelfInfo/getSelfUid/getSelfUin/isSelf/getMsgHistory`、`getFriendRequests/sendFriendRequest/approveFriendRequest`、`setReplyWhitelist/getReplyWhitelist`、`download(url, path)`。
- **ConsoleBridge**：把 JS `console.*` 桥到模块 Log。
- **移植难度：C**（Rhino 引擎与消息/发送类 API 可搬（B 级部分），但 `PluginAPI.hook` 的任意类任意方法运行时 hook 依赖 Xposed 运行时，smali 补丁无法等效）。

### 2.22 cache/ 三件套 + ui/RecalledOverlay + util/Net、Log

- `cache/MsgCache`（692 行）：防撤回数据层。内存 Map（msgId→CachedMsg）+ 落盘 `filesDir/antirecall_cache.json`（Gson）。核心方法：`cache()` 存消息快照；`isRecallRecord(obj)` 判 revokeElement；`restoreInto(obj)` 把缓存的原始 elements 深拷回撤回记录（靠 `ElementReplicator`）；`markRecalled/isRecalled`；`feedTip/takeForTip/takeRecalledSummary`（给灰条供原文）；`isSelfMessage`；`totalSummary`。
- `cache/ElementReplicator`（486 行）：反射深拷贝 MsgElement 对象图（deepCopy/deepCopyValue，IdentityHashMap 防环），供 restoreInto 把原元素塞回记录。
- `cache/CachedMsg`（14 行）：快照数据类（msgId/peerUid/chatType/senderUid/elements 副本/摘要等）。
- `cache/RawMsgStore`（81 行）：原始消息 JSON 环形存（供 HTTP API 查历史 `get_msg_history/get_recent_msgs` 与调试 dump）。
- `ui/RecalledOverlay`（74 行）：一个 Toast/浮层提示工具（show/toast），**全模块无调用点**（grep 仅自身），推测为遗留代码。
- `util/Net`（149 行）：HTTP GET/POST 小工具（翻译/公告/动态等共用）。
- `util/Log`（304 行）：带文件落盘的日志（设置页「运行日志」、插件日志共用）。
- **移植难度：B**（均为纯逻辑，随防撤回一并搬入）。

### 2.23 MainActivity（模块 App 本体，372 行）

- 仅状态页：显示激活/注入状态（经 StatusProvider 读）、运行框架版本、官方群二维码（远程图 `official_group_qrcode.jpg`，加载失败提示群号 801524531）、点二维码看大图（右滑退出）。无设置项（设置全在宿主内 SettingsInjector）。
- **移植难度：不适用**（补丁形态不需要模块 App）。

---

## 3. 设置项清单（SettingsInjector 暴露给用户的全部页面）

入口：宿主设置页新增分类「增强模块设置」（副题「防撤回 / 定时消息 / 端口 / 关于」）；菜单页（openPage 0）条目（文案原文）：

| 页号 | 条目 | 副题原文 |
|---|---|---|
| 1 | 基础设置 | 防撤回 / 翻译接口 / 通用 |
| 2 | 定时消息 | 按时自动发送消息 |
| 5 | 关键词回复 | 按关键词触发回复 |
| 6 | 自动回复 | 私聊收到消息即回复 |
| 3 | 端口设置 | 本地 HTTP 服务器 目前端口 {port} |
| 7 | AI 总结设置 | 自定义 url / apikey / 风格 / 测试 |
| 8 | URL 转语音发送 | 下载音频 / 选择会话发送 |
| 9 | 发送文件 | 选择任意格式文件发送 |
| 10 | 下载管理 | 保存路径 / 查看已下载文件 |
| 13 | 插件管理 | 导入 / 删除 / 启停插件 / 拓展功能 / 运行日志 |
| 15 | QQ 动态 | 定时点赞 / 评论动态 / 白名单 / 日志 |
| 4 | 关于模块 | 版本 / 作者 / 官方群聊 |
| 11 | 官方公告 | 查看最新公告 / 版本更新 |

另有非菜单页：12 公告详情、16 邮箱主页、17 邮箱设置、18 收件箱、19 邮件详情、20 写邮件、21 公告节点、22 邮箱网页背景（由邮箱卡片/公告页跳转进入）。

**基础设置页（buildFang）条目**（文案原文摘录）：
- 「防撤回总开关」——关后不拦截消息撤回（`enabled`）
- 「防撤回模式」二选一：「强制阻止撤回」（推荐 · 红边 + "已撤回"角标，mode=1）/「注入还原」（原文注入"对方撤回"灰条，mode=2）
- 「翻译接口替换」——开启后用自定义网关翻译，关闭沿用 QQMax 内置（`translate_enabled`）
- 「自定义后缀」开关 + 「后缀内容（留空则默认"喵~"）」（`catgirl_enabled`/`catgirl_suffix`）
- 「媒体选择器」三档：「系统媒体选择器」/「QQ 原生选择器」（推荐 · 与转发聊天记录同款界面）/「模块自建弹层」（兼容回退，列表异常时可切换）（`system_media_picker`/`native_picker` 组合）
- 「QQ邮箱功能」——开启后主界面显示 QQ 邮箱会话卡片（`mail_enabled`）
- 「群公告」——开启后可查看本群全部公告，关闭仅显示最新一条 / 去除群公告加载数量限制（`bulletin_all_enabled`）
- 恢复默认：清除全部设置 / 清除关键词回复设置 / 清除定时消息设置 / 清除自动回复设置（Config 字符串证据）

**其它页要点**：
- 定时消息：总开关 + 多规则编辑（时间、会话多选、内容、回复并艾特开关）
- 关键词回复：总开关 + 多规则（关键词、回复内容、适用会话）+ 关键词 AI（url/apikey/model/persona）
- 自动回复：开关 + 回复内容 + 会话白名单（会话选择器）+ 回复并艾特
- 端口设置：HTTP 端口输入（默认 8081，保存后重启服务，日志「保存设置 port=」）
- AI 总结设置：url / apikey / model 输入 + 测试请求 + 风格说明
- 发送文件：选文件、发送模式（`send_mode` 0/1）、分片开关（`shard_enabled`）
- 下载管理：下载路径（`download_dir`）+ 已下载文件列表（打开/删除）
- 插件管理：导入.js、插件与 Feature 启停列表、运行日志
- QQ 动态：开关、时间、间隔、点赞/评论开关、白名单、评论内容、数量、立即执行、日志
- 邮箱设置：邮箱地址/授权码/名称 + 验证；邮箱主页含收件箱入口、未读数、免打扰、置顶、网页背景
- 关于模块：版本、作者、官方群聊 801524531

---

## 4. 移植难度汇总

分级定义：A = 目标方法就在宿主 dex 内、改其 smali 即可等效；B = 需把模块逻辑改写/编译后内联进宿主 dex（工作量在搬代码与改调用点）；C = 依赖 Xposed 运行时/跨进程/框架级拦截/外部服务，smali 直改难以等效。

| # | 功能 | 级 | 理由 |
|---|---|---|---|
| F01 | 防撤回·强制阻止（mode=1） | B | 吞 onMsgDelete 是 A 级小改，但完整功能需 MsgCache 缓存 + restoreInto + c_0 还原一整套数据层内联 |
| F02 | 防撤回·注入还原（mode=2） | B | 需缓存 + markRecalled + 灰条供词逻辑内联 |
| F03 | 已撤回红边/角标、灰条样式 | B | 渲染逻辑要改写进 BaseWatchItemCell/WatchGrayTipsCell 的绑定方法 |
| F04 | 长按菜单：本地编辑消息（已编辑标） | B | LongPressMenu.build 在 classes4.dex，注入行 + 编辑存取逻辑需内联 |
| F05 | 伪语音波形防崩 | **A** | PttAudioWaveView.f 方法头加一段 List 元素类型清洗即可 |
| F06 | 伪媒体识别 + 下载还原行 + 文件详情 | B | 检测/下载/弹窗逻辑成套内联（FakeMediaReceiver、FakeImage/FakeVoice） |
| F07 | 伪图片/伪语音发文件（含分片归集） | B | 纯文件处理 + 元素构造，无 Xposed 专属依赖 |
| F08 | URL 转语音发送 | B | 下载 + 伪语音封装 + 会话选择发送，逻辑内联 |
| F09 | 自定义后缀（喵~） | **A** | MsgServiceImpl.sendMsg（宿主 classes7.dex）在方法内给 textElement.content 尾加后缀即可；只需一个开关/后缀的配置读取小逻辑 |
| F10 | 自动回复 | B | 监听回调内联 + 发送通道（QqKernel 等效反射在宿主内可直用） |
| F11 | 关键词回复 | B | 同上，规则解析代码搬入 |
| F12 | 关键词 AI 回复 | B | 需搬入 HTTP + prompt/解析代码，依赖用户自填 API |
| F13 | 定时消息 | B | 守护线程 + 规则 + 发送，宿主进程常驻可实现 |
| F14 | AI 总结自定义模型 | B | 入口 Summarizer.summarize 就在宿主 classes.dex（改法 A 级简单），但 HTTP/SSE/prompt 整套逻辑需搬入 |
| F15 | 翻译接口替换 | B | 网关代码可搬，但**模块的目标类 Translator 在 M2.5 不存在**，须先定位真实翻译入口（MessageTranslate/HistoryTranslate）再改 |
| F16 | 系统媒体选择器接管相册 | B | MenuFrame/WatchAIOFragment 回调点改写 + 选择器回传处理内联 |
| F17 | QQ 原生选择器桥接 | B | 导航/结果桥接逻辑重，需逐个核对 M2.5 的 Fragment 类名与混淆方法名 |
| F18 | 聊天设置页「添加好友」入口 | B | SettingFrame.onViewCreated 尾加入口 + 内核好友申请调用 |
| F19 | 好友验证问答弹窗 | B | 目标 collect 类 emit 方法在宿主内，弹窗代码内联 |
| F20 | 图片查看器长按保存图片 | **A** | MediaPager$MediaAdapter.onBindViewHolder 末尾给 itemView 加长按监听即可 |
| F21 | 临时会话强制显示 | B | 多点（回调/数据源/渲染/发送放行）逻辑复杂，但目标全在宿主 dex |
| F22 | 群公告全量（去数量限制） | B | 宿主已有 GroupBulletinApi 与 GroupBulletinFragment，把全量拉取+投递逻辑写进宿主对应类即可 |
| F23 | QQ 邮箱（卡片 + 收发） | B | JavaMail 整库 + 管理器 + 卡片注入逻辑搬入，量大但无运行时依赖 |
| F24 | QQ 动态定时点赞/评论 | B | 捕获 hook 改写为宿主方法内联 + 调度线程 + 两条执行通道择一 |
| F25 | HTTP API + 管理后台 | B | 纯 Java socket 服务可整体搬入；建议顺带加回环绑定/token |
| F26 | JS 插件系统 | C | Rhino 与消息类 API 可搬（B），但 PluginAPI.hook 的任意方法运行时 hook 依赖 Xposed，无等效 |
| F27 | 风控绕过（反检测/图灵盾/踢线拦截） | C | hook 框架类（File/Runtime/Debug/SystemProperties/FileInputStream）且跨 MSF 进程，smali 无法全局等效 |
| F28 | MSF 推送保活 | C | 跨 MSF 独立进程 + 前台服务/WakeLock/通知等系统行为，主进程侧仅小部分可改 |
| F29 | 文本可选/复制修复 | C | hook 的是框架类 TextView，宿主内只能逐个改调用点，无法全局等效（局部页面可 B 级处理） |
| F30 | 作者强制公告 | C | 依赖作者远程配置服务（devfile.vip），不建议移植 |
| F31 | 官方群门禁 | C | 作者分发控制，移植时应直接剔除 |
| F32 | 群管理内核 API（踢人/全员禁言/改名/成员列表等） | B | 纯反射调内核，宿主内可直接内联调用；缺的只是 UI 入口（见 §5.3） |

统计：共 32 项功能，**A 级 3 项（F05/F09/F20），B 级 23 项，C 级 6 项（F26–F31）**。

---

## 5. 专项标注

### 5.1 bigface / 表情相关

- **模块没有任何 bigface/超表情功能**：全模块字符串与类清单中无 bigface 相关代码（宿主的 bigface 是 momoi mod 的 `BigfaceRes`，与本模块无关）。与表情沾边的只有三处：
1. `QqKernel.createFaceElement(int)` / PluginAPI `sendFace(chatType, peerUid, faceId)`：构造系统表情元素发送（供 HTTP API/插件用）。
2. AiSummaryHook/关键词 AI 与总结无关表情。
3. 宿主侧参考：模块 hook 的 `MsgListUtilKt.c_0`、`LongPressMenu.build` 与 bigface 所在的 classes4.dex 是同一个 dex，移植防撤回时注意别与已有 bigface 补丁冲突（改同一文件需合并）。
- 结论：修 bigface 的 bug 与本模块无关；但移植本模块功能时，F01/F03/F04 的改动点和 bigface 补丁同 dex 共存，需注意 smali 合并顺序。

### 5.2 消息防撤回（模块主干，细节）

- **数据流**：所有进出消息先被 `callbackCacheList/callbackAddSendMsg` 缓存（MsgCache 内存 Map + `antirecall_cache.json` 落盘 + RawMsgStore 存原始 JSON），这是两种模式共同的地基；**没有缓存就没有还原**。
- **mode=1 强制阻止**（默认）：三重拦截——① onMsgDelete 类回调 before 里 `setResult(null)` 吞掉删除（仅限 void 方法）；② 消息列表再同步到撤回记录时，`callbackCacheList` 调 `restoreInto` 把缓存的原 elements 深拷回记录对象；③ UI 侧 `MsgListUtilKt.c_0` before 再做一次 restoreInto，保证渲染成原气泡。之后 BaseWatchItemCell 绑定时按 `isRecalled` 打红边 + 「」角标（样式对账线程持续修正重绑丢失的样式）。
- **mode=2 注入还原**：不拦截删除；onMsgDelete 时 `markRecalled(msgId)`；撤回灰条绑定时（WatchGrayTipsCell.d after）从缓存取原文摘要注入灰条显示（`takeForTip`），即「XX 撤回了一条消息：<原文>」。
- **自身消息不描边**：`isSelfMessage` 跳过自己消息的撤回标记（日志「删除/撤回跳过自身消息(不描边)」）。
- **撤回他人消息（主动）**：模块另有 `QqKernel.recallMsg`（内核 `IKernelMsgService.recallMsg(Contact, ArrayList<Long>, IOperateCallback)`），供 HTTP API/插件撤回**自己**发出的消息，与防撤回是两回事。
- **移植要点**：监听器类 `WatchAIOFetchService$registerMsgListener$2` 的 onRecvMsg/onMsgInfoListAdd/onMsgInfoListUpdate/onAddSendMsg/onMsgDelete 即宿主内的等效改点；全局版改点为 `com.tencent.qqnt.msg.MsgService$init$1` 同名方法。

### 5.3 群管理相关（与「长按头像禁言/踢人」需求直接相关）

模块**没有**群内长按菜单类的群管理 UI，但 `http/QqKernel` 已实现一整套可直接复用的群管理内核调用（经 HTTP API 与 PluginAPI 暴露），且宿主 M2.5 内核签名已逐一核对存在：

| QqKernel 方法 | 内核调用（宿主 IKernelGroupService，classes2.dex） | 宿主签名核对 |
|---|---|---|
| `kickMember(groupId, memberUidOrUin, reason)` | `kickMember(long, ArrayList<String>, boolean=false, String, IKickMemberOperateCallback)`，动态代理回调 + 8s 超时；入参纯数字 uin 先经 `uinToUid` 换算，必须是 `u_` 开头 uid | `kickMember(JLjava/util/ArrayList;ZLjava/lang/String;LIKickMemberOperateCallback;)V` ✔ |
| `setGroupShutUp(groupId, boolean, timeout)` | `setGroupShutUp(long, boolean, IOperateCallback)` —— **全员禁言**开/关 | `setGroupShutUp(JZLOperateCallback;)V` ✔ |
| `banAllStatus(groupId)` | 读群详情 `GroupDetail.shutUpAllTimestamp` 判断全员禁言状态（BanResult{muted, shutUpAllTimestamp}） | ✔（字段反射） |
| `getGroupMembers(groupId)` | `getAllMemberList(long, boolean, IGroupMemberListCallback)`，返回成员 JSON：uid/uin/nick/cardName/role/**shutUpTime**/joinTime/lastSpeakTime/isRobot | `getAllMemberList(JZIGroupMemberListCallback;)V` ✔ |
| `isGroupOwnerOrAdmin(groupId, uid)` | 读成员 role 判断群主/管理员（HTTP/插件侧权限校验用，带 ROLE_TTL_MS 缓存） | ✔ |
| `modifyGroupName / modifyGroupRemark / quitGroup` | 对应内核改名/改备注/退群调用（IOperateCallback 代理） | 方法存在（QqKernel 内反射按名查找） |
| `fetchGroupNotices / registerGlobalBulletinListener` | 群公告全量，见 §2.16 | GroupBulletinApi 在宿主 ✔ |

- **⚠ 个人禁言模块未实现**：QqKernel 里没有「禁言单个成员」的方法（只有全员禁言 setGroupShutUp）。但宿主内核存在现成接口（本次核对新发现）：`IKernelGroupService.setMemberShutUp(JLjava/util/ArrayList;Lcom/tencent/qqnt/kernel/nativeinterface/IOperateCallback;)V`（classes2.dex）——移植「长按头像禁言（可选时长）」应基于此接口新写，ArrayList 元素结构（uid + shutUpTime 的载体类）需在实现时再从宿主 dex 确认。
- **长按入口参考**：模块现有的长按注入点是**消息**长按菜单 `LongPressMenu.build(AIOLongClickMenuFragment, LayoutInflater, MsgRecord, WatchAIOMsgItem, FragmentManager, boolean)`（classes4.dex），从 MsgRecord 可取 senderUid/peerUid/chatType——若要做「长按头像」，宿主 mod 另有 `momoi.mod.qqpro.hook.menu.MessageMenuKt`（含 `memberOf$1/memberOf$4`、`recallEligible$1` 等 lambda，推测为消息菜单的成员判定逻辑）与 `momoi.mod.qqpro.hook.view.MemberPickerFragment`、`momoi.mod.qqpro.api.GroupAPI#getMemberInfo(long, long, Function1)`（classes4.dex）可作现成参考点（本次未深入，标注为推测）。

---

## 6. 优先移植建议（Top 5）

1. **F32 群管理内核 API（含新写 setMemberShutUp 个人禁言）**——当前明确需求（长按头像禁言/踢人）的地基，内核签名已全部核对存在，QqKernel 的 kickMember 实现可直接照搬改写。
2. **F01+F02 防撤回双模式（含 MsgCache 数据层）**——模块主干、用户价值最高，改点（监听器类 + c_0）均已核对在宿主存在。
3. **F03 已撤回红边/角标 + 灰条原文样式**——与防撤回配套，渲染改点明确（BaseWatchItemCell/WatchGrayTipsCell）。
4. **F09 自定义后缀**——A 级最小改动，改 MsgServiceImpl.sendMsg 一处即可见效，适合作为移植管线的首个验证样例。
5. **F22 群公告全量**——宿主已有 GroupBulletinApi/GroupBulletinFragment 现成承接点，改动面小、体验提升直接。

（F05 伪语音波形防崩同为 A 级，可顺手与 F09 一起做。）
