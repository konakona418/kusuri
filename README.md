# Kusuri(薬)

个人用药提醒与记录应用。**无遥测**,所有数据只保存在你的设备上。

## 功能

- **提醒**:四种调度模式——每天固定时间、间隔制(每 N 小时/天,固定锚点)、按需(PRN)、疗程;外加饭前/饭后/随餐标签
- **通知**:普通通知(非闹钟式),提醒等级可选静默 / 横幅;通知上直接「已服用 / 稍后 15 分 / 跳过」
- **可靠性**:首次向导检查通知权限、精确闹钟、电池优化白名单;开机/改时间/换时区后自动重排;WorkManager 定期巡检
- **记录**:打卡、补记、跳过;错过按"计划时间 + 宽限窗口"派生,误判可改
- **历史**:按天时间线(服药记录与症状混排)+ 近 7/30 天遵守率
- **日志**:症状(1–5 程度,可选关联某味药)+ 随手记;单症状近 30 天趋势
- **库存**:补货 / 盘点调整 / 低库存提醒(提醒一次,补货后重新武装)
- **备份**:JSON 完整备份与恢复(含日志,兼容旧版本);CSV 分「服药记录 / 症状与随笔」两段,方便给医生看
- **局域网导出**:在你手动开启时,把同样的 JSON / CSV 经局域网直连推送到你自己电脑上的接收脚本(扫码或手输地址);手机不监听任何端口,数据不经任何服务器

## 技术

- Kotlin + Jetpack Compose(Material 3),单 `:app` 模块,手动依赖注入
- Room 存储(带版本迁移);排程引擎为纯 Kotlin 领域代码并注入 `Clock`,便于单测
- `AlarmManager` 精确闹钟 + WorkManager 巡检;SAF 读写文件,导出不需要网络
- 局域网导出走手写裸 socket 的单一 POST,不引 HTTP 客户端库、不碰明文策略;扫码用 CameraX + zxing(不引 Google 服务)

## 权限

| 权限 | 用途 |
|---|---|
| `POST_NOTIFICATIONS` | 用药提醒通知(Android 13+ 运行时申请) |
| `SCHEDULE_EXACT_ALARM` | 到点准时提醒;被拒时降级为非精确并在设置里明示 |
| `RECEIVE_BOOT_COMPLETED` | 开机后重排提醒 |
| `VIBRATE` | 提醒震动 |
| `CAMERA` | **只**用于扫局域网导出的那个二维码;运行时申请,拒绝后仍可手输地址 |
| `INTERNET` | **只**用于你手动开启的那次局域网导出:直连你输入的电脑地址 |

关于 `INTERNET`:应用里没有 HTTP 客户端库、没有统计/崩溃上报、没有任何服务器地址;整个仓库里唯一的网络代码是 `data/lan/LanExportClient.kt`(一次 POST)与配套的电脑端接收脚本 `tools/kusuri-receive.py`(不在 APK 里)。契约、安全边界与审计指引见 [`docs/lan-export.md`](docs/lan-export.md)。

## 构建与安装

```bash
./gradlew :app:testDebugUnitTest     # 单元测试 + Robolectric 数据库/备份测试
./gradlew :app:installDebug          # 装调试包
./gradlew :app:installRelease        # 装正式包(需本地 keystore,见下)
```

正式包需要本地签名(签名材料不入库):

- keystore 与凭据放在仓库外(`~/.android-keys/`、`~/.gradle/gradle.properties`)
- 详细步骤与发布前检查清单见 [`docs/release.md`](docs/release.md)

## 局域网导出

手机上导出 JSON/CSV 时,除了存到本机,还可以直接推到你的电脑:

```bash
uv run tools/kusuri-receive.py          # 电脑上跑这个;它会打印地址、会话口令和二维码
```

然后手机:**设置 → 备份与导出 → 局域网导出(扫码到电脑)**,扫码(或手输 `192.168.2.110:47821#K4F9M2`,同网段可写短式 `110#K4F9M2`)即可。
需要两端在同一局域网;明文 HTTP,**最严谨的用法是手机开热点、电脑连热点**。完整契约与安全边界见 [`docs/lan-export.md`](docs/lan-export.md)。

## 文档

- [`docs/plan.md`](docs/plan.md) — 设计决策、领域模型、调度语义、里程碑(M1–M7)
- [`docs/lan-export.md`](docs/lan-export.md) — 局域网导出协议契约、安全边界、审计指引
- [`docs/release.md`](docs/release.md) — 签名、R8、发布检查清单
