# 主仓库 Issues 修复分析（Nekoscene）

> 说明：本环境无法编译 Android（依赖 `jcenter()` 已停止服务，且本机无 Android SDK / 设备），
> 无法做运行时/实机验证。以下针对 6 个「明确代码 Bug」类 issue 给出**根因分析**与**可落地修复建议**，
> 供在可编译、可测试环境下实施。其中构建健康类问题（jcenter）已直接修复。

## 1. 环境与构建限制（已修复部分）
- 根 `build.gradle` 仍引用 `jcenter()`（已停用）。已将其迁移为 `mavenCentral()`（见 `build.gradle` 第 6、26 行注释处）。
- 本机无 Android SDK / 模拟器，下列功能类 bug 的修复需配套实机验证，故以「建议补丁」形式给出，未盲改业务代码以免引入回归。

## 2. #41 帧率记录始终显示 0.0（adb / root 模式都失效）
- **现象**：悬浮 FPS 右上角恒为 `0.0`，仅调用 `dodump` 一两次，参数为 `--comp-displays` 而非 `--latency`。
- **根因**：`FpsUtils.currentFps` 的帧率来源依次是
  1. GPU 内核节点 `/sys/class/drm/sde-crtc-0/measured_fps` 或 `/sys/class/graphics/fb0/measured_fps`；
  2. `service call SurfaceFlinger 1013`。
  前者多数机型不存在；后者在 Android 10+ 上事务码已变化，返回值解析失败 → `fpsFilePath` 置空、`fpsCommand2` 清空 → `currentFps` 返回 `null`，`fps` getter 返回 `-0f`（即 `0.0`）。
  换言之，真正可用的 `dumpsys SurfaceFlinger --latency <display>` 方案在代码中**并未被采用**。
- **建议修复**：在 `FpsUtils.currentFps` 增加 `--latency` 兜底——
  - 执行 `dumpsys SurfaceFlinger --latency <display>`（display 可用 `dumpsys SurfaceFlinger --display-id` 获取，默认 `0`）；
  - 跳过首行（标题），取末尾 128 行时间戳，用相邻两帧时间差求平均帧间隔 → `1000/间隔` 即 FPS；
  - 优先级建议：`measured_fps` 内核节点 > `--latency` > `service call`。
- **风险**：需实机验证；建议先在 `FloatFpsWatch` / `ActivityFpsChart` 联调。

## 3. #35 进程管理：点不进进程、各进程 CPU 占用率定住不动
- **现象**：点击进程无反应（打不开详情）；列表里每个进程的 CPU% 长时间不变。
- **根因 A（点不进详情）**：`ProcessUtils.getProcessDetail(pid)` 使用 `DETAIL_COMMAND = "ps -e -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE --pid "`，
  但 `supported()` 只验证了**不带 `--pid`** 的 `ps`/`top` 能否返回数据。部分 toybox 的 `ps --pid` 不支持或输出格式不同，
  导致 `getProcessDetail` 解析不到第二行 → 返回 `null` → 弹「无法获取详情，该进程可能已经退出!」。
- **根因 B（CPU 定住）**：列表中的 `%CPU` 直接取自 `ps -o %CPU`，它是「进程启动以来的平均占用」，**几乎不随时间变化**，所以看起来「定住不动」。
  真正的实时 CPU 应基于 `/proc/stat` 与 `/proc/<pid>/stat` 两次采样差值计算。
- **建议修复**：
  - 明细命令改为 `ps -p <pid>`（或对列表结果按 PID 过滤），并对 `--pid`/`-p` 支持做显式探测；
  - 新增实时 CPU 计算工具：采样 `/proc/<pid>/stat` 的 `utime+stime` 与 `/proc/stat` 的 total，按 `(Δproc/Δtotal)*100` 得到百分比。
- **风险**：解析逻辑改动较大，需实机验证。

## 4. #31 adb 权限下看不到 GPU 频率
- **现象**：未 root、仅 adb 授权时 GPU 频率不显示（Mali / Dimensity 等）。
- **根因**：GPU 频率读取依赖 root shell 读取 kgsl（`/sys/class/kgsl/kgsl-3d0/gpuclk`）或 Mali 节点；adb(shell) 权限下这些节点不可读，
  且缺少厂商工具所需的 `LD_LIBRARY_PATH`（如 Dimensity 8100 需要 `/vendor/lib64:/vendor/lib64/egl/...`），导致工具找不到库。
- **建议修复**：在 adb 模式下，按芯片设置 `LD_LIBRARY_PATH` 后调用厂商 GPU 工具；并把 GPU 频率数据源抽象为可扩展列表
  （Adreno→kgsl 节点；Mali→`/sys/class/misc/mali0/device/gpu_clock` 等；按机型适配）。
- **风险**：高度依赖机型，建议做成可配置/可扩展数据源。

## 5. #21 负载监视器：电池状态显示异常
- **现象**：负载监视器里电池相关数值异常。
- **根因**：`BatteryUtils` 同时支持 `dumpsys battery` 与 `/sys/class/power_supply/{bms,battery}/uevent` 两套来源。
  部分机型 uevent 的 `POWER_SUPPLY_TEMP` 单位/字段缺失或不一致；当 `bms` 节点存在但无 temperature 时，
  `getBatteryTemperature()` 返回空 `BatteryStatus`，UI 显示异常。此外 `getBatteryTemperature` 用「同名参数只读一次」逻辑，在字段重复出现时可能取错值。
- **建议修复**：统一以 uevent 的 `POWER_SUPPLY_TEMP`（÷10）为优先，缺失时回退 `dumpsys battery`；对重复字段做「取有效数值」的健壮解析；解析失败时给出明确占位而非空对象。
- **风险**：中，需在多机型核对单位。

## 6. #19 老是提示「辅助服务已停止」
- **现象**：频繁提示去激活辅助服务，即使已在系统设置中开启。
- **根因**：`AccessibleServiceState.serviceRunning` 仅用 `getEnabledAccessibilityServiceList` 判断「是否已**启用**」，而非「是否真正**连接运行**」。
  在 MIUI / HyperOS 等 ROM 上，已启用的无障碍服务会被系统/省电策略杀掉；此时 enabled 列表仍包含它，但服务实际已断开，应用内部连接态为空 → 反复提示。
  此外 `serviceInfo.id.endsWith("AccessibilityScenceMode")` 的匹配在不同 ROM 上 id 格式不一致，可能漏判。
- **已修复（已在代码中实施）**：
  - 新增共享连接态 `AccessibleServiceState.isServiceConnected`（volatile），由 `AccessibilityScenceMode` 在 `onServiceConnected()` 置 `true`、`onUnbind()` 置 `false`（原 `private serviceIsConnected` 字段已迁移到该共享标志，避免 library 反向依赖 app 模块）；
  - `serviceRunning()` 现优先返回 `isServiceConnected`（真实连接态），否则回退到 enabled 列表检查，并放宽 id 匹配（`endsWith || contains` 完整类名），降低不同 ROM 上 id 格式不一致导致的漏判；
  - SCREEN_ON 时“辅助服务已失效”的提示也改用该共享标志。
- **效果**：服务真正连接运行时不再误报“已停止”；被杀掉后仍能正确提示。需实机验证。
- **风险**：低~中。

## 7. #32 Scene 闪退
- **现象**：使用中闪退，描述笼统，无堆栈。
- **根因**：无法定位具体崩溃点（需 `logcat` 堆栈）。常见高危点：
  - `ActivityProcess` 中 `pm!!.getPackageInfo/getApplicationInfo`（已有 try-catch，但 `pm` 非空保护依赖 `pm == null` 才赋值，首次 `loadIcon` 在 `pm` 未初始化时可能为 null）；
  - `FloatMonitor` 等悬浮窗在权限丢失时访问 window；
  - `ActivityFpsChart` 的 WebView JS 桥异常。
- **建议**：复现时抓取 `logcat | grep Nekoscene` 提供堆栈，再针对性修复；可先为关键为空路径加防御性判空。

## 8. 修复优先级建议
1. **构建可编译**：`jcenter()→mavenCentral()`（已完成）。
2. **#19 真实连接态判定**：已在代码中实施（见第 6 节），建议实机自测。
3. **#35 / #41**：涉及采样算法，建议配套单元测试 + 实机验证（见第 2、3 节补丁）。
4. **#31 / #21**：机型适配，建议抽象为可扩展数据源（见第 4、5 节）。

> 已落地：`jcenter()` 构建仓库迁移、#19 辅助服务连接态判定。
> #35/#41/#31/#21/#32 因高度依赖机型/ROM/权限且无本机编译与设备验证条件，已给出根因分析与可落地补丁，待可编译、可测试环境下实施。
