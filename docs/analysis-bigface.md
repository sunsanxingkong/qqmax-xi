# QQ Max M2.5「点 bigface 出问题」静态排查报告

- 目标：`QQ_Max_M2.5_0_85sc.apk`，48 MB，MD5 `739a1a58e4b16b63c9a94509ad34bc40`，SHA-256 `fe82d0b62b374272cedb770e439160e2f5b277f04fbe2567268207575414ffad`
- 身份：`com.tencent.qqlite`，versionName `M2.5` / versionCode `2465`，minSdk 21 / targetSdk 28，启动 Activity `com.tencent.qqnt.watch.app.JumpActivity`，签名 = AOSP TESTKEY（SHA-256 `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`）——即第三方重签 mod 包，不是腾讯原签。
- 范围/限制：**纯静态**（jadx API 定向反编译 + dexdump/smali 流式核对 + Python 解析 zip/dex/PNG）。未真机运行，未拿到用户所说的具体现象（闪退？空白？没图？）。用户原话只有「修复bug / 点big / 点bug」，**bigface 只是最合理解释，未经用户确认**；包内另有 mod 的 `BigImageFragment`（点大图查看），若用户指的是那个，本报告不覆盖。
- 本次**未改包、未交付任何文件**，只做调查。

## 1. bigface 是什么：mod 注入功能，不是官方功能

官方 QQ 手表版原本没有这批大表情。mod 作者（`momoi.mod.qqpro`）做了三件事：

1. 往 APK 里塞了 `assets/bigface.zip`（8.5 MB，137 条目）和 `assets/bigface_index.json`（33 个 id：419–427、429–432、450–469）。
2. 新增类 `momoi.mod.qqpro.lib.BigfaceRes`（Kotlin，源文件 `BigfaceRes.kt`，只在 **classes4.dex** 有类定义）负责解压资源 + 往官方表情配置里追加条目。
3. 直接改了官方类 `com.tencent.mobileqq.emoticon.QQSysFaceResImpl.parseConfigData(JSONObject, JSONObject)`（**classes.dex**）：改名原方法为 `parseConfigData_0`，新方法先调 BigfaceRes 注入、再调原方法。日志标签 `EmojiConfigPatch`。

## 2. 相关类 / 方法清单

| 类 | 所在 dex | 关键方法 | 作用 |
|---|---|---|---|
| `momoi.mod.qqpro.lib.BigfaceRes` | classes4.dex（定义） | `ensureExtracted(Application)` | 把 `assets/bigface.zip` 解压到 `filesDir/qq_emoticon_res/`，以标记文件 `.qqpro_bigface_v1` 判「已解压」 |
| 同上 | classes4.dex | `animatedIds(Application): List<String>` | 读 `assets/bigface_index.json` 的 `ids` 数组，全程 try/catch，失败返回空列表 |
| 同上 | classes4.dex | `appendMissingSysface(Application, JSONArray): int` | 先按已有 QSid 去重，再追加 39 条硬编码 `CLASSIC_STATIC_ENTRIES`（经典表情，如 QSid 245/AQLid 217）+ 33 条动画条目（QSid=AQLid=IQLid=id，`AniStickerType=1, AniStickerPackId="1", AniStickerId=id, QDes="/超表情"+id, EMCode="10"+id`） |
| `com.tencent.mobileqq.emoticon.QQSysFaceResImpl` | classes.dex（被 mod 改过） | `parseConfigData`（新包装，smali 地址 0x650748，catch 区间 0x0004–0x005c → 0x005d） | 先 `BigfaceRes.ensureExtracted(Utils.getApplication())`，再对 faceConfig 的 `sysface` 数组调 `appendMissingSysface`，异常只打日志（`EmojiConfigPatch error:`），最后无条件调 `parseConfigData_0` |
| 同上（官方原逻辑） | classes.dex | `parseConfigData_0` / `parseConfigInner` | 逐条 `Integer.parseInt(AQLid/QSid)` 建 `mConfigItemMap/mServerToLocalMap/mOrderList/mAniStickerOrderList`，单条异常被逐条 catch（`error occur in sysface AQLid:`），不会整表崩 |
| 同上 | classes.dex | `getDrawable / getGifDrawable / getAniStickerInfo / getDescription / getServerId…` | 按 localId 取图/超表情信息；`getAniStickerInfo` 查不到返回 null |
| `com.tencent.mobileqq.emoticon.QQSysAndEmojiResMgr` | classes4.dex（定义） | `getResSavePath()` / `getFullResPath(type, id)` / `getFullResPathV2(type, id)` / `getAniStickerResPath(pack, id)` | 官方资源根目录就是 `filesDir/qq_emoticon_res/`（常
...[truncated 7158 chars]