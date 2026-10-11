# 上游 N1 版本差距分析（Nekoscene scene5 ←→ Scene N1 2026.10 Alpha13）

> 生成时间：2026-10-11
> 分析对象：`scene_N1_2026.10_Alpha13.apk`
> 对比基线：本仓库 `scene5` 分支（Nekoscene 5.0.0）

## 一、结论先说

**完整「同步代码」不可行。** 原因有三：

1. **不是同一代代码**。我们的 `scene5` 是 Kotlin 1.4.10 / AGP 4.0.1 / Gradle 6.5 / minSdk 23；
   上游 N1 是 Kotlin 1.8.22 / Gradle 8.4 / JDK 17 / minSdk 26 / targetSdk 36。中间隔了一整代重构。
2. **上游 APK 经过 R8 混淆**。3046 个类里只有 178 个保留 `com.omarea.*` 原名，
   其余全部被改名成 `a.a0` / `a.z` 这类（`SourceFile` 也被抹掉）。反编译出来是混淆代码，无法直接回填。
3. **功能量级差一个数量级**。上游 1987 个 string 键，我们只有 438 个；上游 68 个 manifest 组件，我们 47 个。

**已经能同步、且已经同步的**：
- ✅ `assets/`（kr-script 脚本、XML 页面、配置）—— 全量替换为 N1 版本，582 → 204 个文件
  - ⚠️ **例外**：`assets/powercfg/`（65 个文件）**已从备份恢复保留**。
    原因：`scene_mode/CpuAffinity.kt:21` 会读 `assets/powercfg/$platform/powercfg_extra.json`，
    N1 已删除该目录，全量替换会让「CPU 亲和性」功能静默失效（有 try/catch 兜底，不会崩）。
    最终 assets = N1 的 204 + powercfg 的 65 = **269 个文件**。
- ✅ 版本号 —— `versionName = "Neko5.0.0 Alpha13"`、`versionCode = 20261013`
- ✅ 应用图标 —— 用你的 `Component_1.svg` 重做的自适应图标集

**替换后做过的引用校验**：全项目扫描 `assets.open("...")` 与 `file:///android_asset/...` 引用，
共 7 个静态路径。除 `powercfg/` 外，另有 4 处（`kr-script/pages/more.xml`、
`kr-script/pages/favorites.xml`、`kr-script/toolkit`、`kr-script/before_start.sh`）在**替换前就已失效**
（`kr-script/pages/` 目录新旧版本都不存在，`before_start.sh` 常量已置空），属历史遗留问题，非本次引入。

**需要你决策的**：下面第三、四节列出的差距，哪些要移植（移植 = 重写，不是复制）。

---

## 二、上游 Alpha13 基本档案

| 项目 | 值 |
|---|---|
| 包名 | `com.omarea.vtools` |
| versionName | `N1 2026.10 Alpha13` |
| versionCode | `920261006`（= `9` + `20261006`） |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| 构建链 | Kotlin 1.8.22 · Gradle 8.4 · JDK 17 |
| 类总数 | 3046（其中保留原名的仅 178） |
| string 资源 | 1987 个 |
| manifest 组件 | 68 个 |
| APK 内接口 | 更新：OSS `vi/SceneN1.json`（**已 404**）· Magisk 模块：`magisk-modules-repo.github.io/submission/modules.json`（正常） |

### 权限差异

**上游新增（我们没有）**
`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、
`GET_APP_OPS_STATS`、`INTERACT_ACROSS_USERS`、`PACKAGE_USAGE_STATS`、`POST_NOTIFICATIONS`、
`READ_PRIVILEGED_PHONE_STATE`、`REQUEST_COMPANION_RUN_IN_BACKGROUND`、
`REQUEST_INSTALL_PACKAGES`、`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`

**我们独有（上游已删）**
`ACCESS_SUPERUSER`、`MODIFY_AUDIO_SETTINGS`、`MODIFY_PHONE_STATE`

---

## 二·补、本次 assets 替换新增的 109 个文件

`kr-script/mtk`（MTK 功耗表）、`kr-script/oppo`（含 `extreme_gt`）、
`kr-script/qualcomm`（DDR/LLCC/L3 调压）、`kr-script/vivo`、`kr-script/img`（boot/TWRP 镜像）、
`kr-script/flyme`、`kr-script/display`、`kr-script/other/thermal`、`kr-script/other/resurgence`、
`kr-script/developer/hwui_*`，
以及根目录的 `categories.json`、`custom.json`、`custom_fas.json`、`fas_*.txt`、
`ksu.zip`、`refresh_rate.txt`、`outside.json`、`toolkit/binary_exist.sh` 等。

---

## 三、Manifest 组件差距

### 上游有、我们缺（34 个）

**Activity（27 个）**

| 组件 | 推测功能 |
|---|---|
| `ActivityFiles` / `ActivityFilesTreemap` | 内置文件管理器（含磁盘占用树图） |
| `ActivityEditor` + `EditorFileService` + `permission.EDITOR_FILE_SERVICE` | 内置文本编辑器（独立进程服务） |
| `ActivityFpsSession` / `ActivityFpsSessions` | FPS 记录会话（历史回放/导出 CSV） |
| `ActivityPerfBench` / `ActivityPowerBench` | 性能基准 / 功耗基准测试 |
| `ActivityPerfOptions` | 性能调度参数设置 |
| `ActivityPowerStat` | 功耗统计 |
| `ActivityChargeControl` / `ActivityChargeStat` | 充电控制 / 充电统计 |
| `ActivityModuleDetail` / `ActivityModuleUpload` | Magisk 模块详情 / 上传分享 |
| `ActivityMonitorDesigner` | 悬浮监控面板自定义设计 |
| `ActivityOplusORMS` | OPPO/一加 ORMS 配置 |
| `ActivityMiuiCloudProfile` | MIUI 云控配置（温控） |
| `ActivityScreenTest` | 屏幕检测 |
| `ActivityThreadsStat` | 线程统计 |
| `ActivityInstaller` | 安装器 |
| `ActivityCommandList` | 命令列表 |
| `ActivityFastShare` | 快速分享 |
| `ActivityApplications` / `ActivityAppContents` / `ActivityAppActivity` / `ActivityAppActivities` / `ActivityAppComponents` | 应用管理拆分成的 5 个子页 |
| `ActivityActionPage` | 替代我们的 `ActionPage` |

**Service / Provider / 其他（7 个）**

| 组件 | 推测功能 |
|---|---|
| `SceneAppWidgetProvider` | 桌面小组件（我们没有） |
| `KeepAliveService` | 保活服务 |
| `AccessibilitySceneMode` | ⚠️ 我们叫 `AccessibilityScenceMode`（拼写 Scence→Scene 上游已修正） |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AGP 新版自动生成的动态广播权限 |

### 我们有、上游已重构/移除（13 个）

`ReceiverCompileState`、`ActionPage`、`ActivityApplistions`、`ActivityCharge`、
`ActivityChargeController`、`ActivityFpsChart`、`ActivityPowerUtilization`、
`ActivitySystemScene`、`ActivityTimingTask`、`ActivityTrigger`、
`scene_mode.SceneTaskIntentService`、`scene_mode.TriggerExecutorService`、`AccessibilityScenceMode`

> 这批基本都被上游改名/合并了（例：`ActivityFpsChart` → `ActivityFpsSession(s)`；
> `ActivityCharge` + `ActivityChargeController` → `ActivityChargeControl`）。

---

## 四、功能差距（按 string 资源键统计）

上游独有的 string 键共 **1495** 个（已过滤 androidx/material 等库前缀），按功能模块分组：

| 前缀 | 新增键数 | 功能 |
|---|---:|---|
| `kr_` | 220 | kr-script 页面（与 assets 同步相关） |
| `apps_` | 129 | 应用管理（批量卸载/备份、AppOps 备份…） |
| `fs_` | 112 | **文件管理器（全新）**：复制/移动/压缩/多标签 |
| `schedule_` | 74 | 定时调度（自动更新、云控下发、GPU Boost…） |
| `user_` | 68 | **账号系统**：注册/登录/激活码/设备绑定/积分兑换 |
| `fps_` | 62 | FPS 会话：裁剪、导出 CSV、云端分享 |
| `pb_` | 52 | 性能/功耗基准（cheese 测试、CPU/DDR 频率…） |
| `swap_` | 43 | 交换内存（ZRAM/swap 文件、模块化） |
| `power_` | 38 | 功耗统计（曲线、应用分组、9008/EDL） |
| `freezer_` | 37 | 应用冻结（自动导入、图标保留…） |
| `rr_` | 35 | **刷新率控制（全新）**：按应用/电量/亮度阈值 |
| `miui_` | 32 | MIUI 温控导入导出 |
| `perf_` | 30 | 性能调度（cpuset、governor、核心选择） |
| `scene_` | 28 | 契约/ADB/权限引导 |
| `scheduler_` | 27 | FAS 调度器参数 |
| `detail_` | 26 | 应用详情子项 |
| `lmke_` | 25 | 内存管理（kswapd0 boost、黑白名单） |
| `process_` | 25 | 进程管理（分组、沙箱） |
| `task_` | 25 | 触发任务 |
| `img_` | 23 | 镜像备份/刷写（boot/vendor_boot/TWRP） |
| `license_` | 22 | ⚠️ 授权/订阅（= 我们已移除的 VIP 体系） |
| `menu_` | 21 | 菜单结构 |
| `xp_` | 20 | Xposed 模块项 |
| `dialog_` | 19 | 各类对话框 |
| `settings_` | 19 | 设置项 |
| `file_` | 18 | 文件打开/安装 |
| `am_` | 17 | Activity Manager（组件/快捷方式查看） |
| `monitor_` | 16 | 悬浮监控（FPS/温度/进程/线程） |
| `module_` | 15 | 模块仓库（作者/排名/分享） |
| `orientation_` | 13 | 屏幕方向控制 |

> ⚠️ `user_*`（68）与 `license_*`（22）是上游的账号 + 授权订阅体系。
> 我们此前已按你的要求移除 VIP/打赏入口，**这部分不建议同步**。

---

## 五、反编译产物（已生成）

输出目录：`D:\nekoscene-re\`

| 文件 | 大小 | 说明 |
|---|---|---|
| `apk_classes_dump.txt` | 9.5 MB | 全量类/字段/方法清单（dexdump） |
| `apk_disasm.txt` | 57.6 MB | 全量反汇编（dexdump -d，Dalvik 汇编） |
| `apk_resources.txt` | 1.3 MB | 全量资源表（8908 条，含所有字符串值） |
| `apk_manifest_tree.txt` | 56 KB | AndroidManifest 完整树 |
| `apk_comp.txt` | 3 KB | 上游组件清单 |

> 未能拿到 Java 级反编译（jadx）。沙箱里 `github.com` 直连被拦，
> 只能走 `api.github.com` 资产接口，实测速度约 0.7 MB/分，105 MB 的 jadx 包需两个多小时。
> 若需要 Java 源码，建议你在本机跑一次：
> `jadx -d out scene_N1_2026.10_Alpha13.apk`（jadx-gui 直接拖进去也行）。

**怎么查具体类**：在 `D:\nekoscene-re\apk_disasm.txt` 里搜类描述符或字符串常量，例如
搜 `Lcom/omarea/vtools/activities/ActivityMain;` 定位类，搜 `const-string` 看它用了哪些 URL / 配置键。

---

## 六、建议的同步优先级

| 优先级 | 项目 | 理由 |
|---|---|---|
| P0 ✅ 已完成 | assets 全量替换、版本号、图标 | 零风险、立竿见影 |
| P1 | `AccessibilityScenceMode` → `AccessibilitySceneMode` 改名 | 纯拼写修正，改动可控 |
| P1 | 补齐 `assets` 对应的 `kr_*` 字符串（220 个） | 新脚本页面需要文案，否则显示 key |
| P2 | `SceneAppWidgetProvider` 桌面小组件 | 独立新增，不动存量代码 |
| P2 | `rr_*` 刷新率控制 | 独立新功能，价值高 |
| P3 | `fs_*` 文件管理器 | 工作量大（112 个字符串 + 2 个 Activity + 树图控件） |
| ❌ 不建议 | `user_*` / `license_*` 账号与授权体系 | 与已移除的 VIP 冲突 |
