# Kusuri(薬)

个人用药提醒与记录应用。**完全离线、无遥测**,所有数据只保存在你的设备上。

## 功能

- **提醒**:四种调度模式——每天固定时间、间隔制(每 N 小时/天,固定锚点)、按需(PRN)、疗程;外加饭前/饭后/随餐标签
- **通知**:默认响铃 + 震动(普通通知,非闹钟式),可在设置里切静音;通知上直接「已服用 / 稍后 15 分 / 跳过」
- **可靠性**:首次向导检查通知权限、精确闹钟、电池优化白名单;开机/改时间/换时区后自动重排;WorkManager 定期巡检
- **记录**:打卡、补记、跳过;错过按"计划时间 + 宽限窗口"派生,误判可改
- **历史**:按天时间线(服药记录与症状混排)+ 近 7/30 天遵守率
- **日志**:症状(1–5 程度,可选关联某味药)+ 随手记;单症状近 30 天趋势
- **库存**:补货 / 盘点调整 / 低库存提醒(提醒一次,补货后重新武装)
- **备份**:JSON 完整备份与恢复(含日志,兼容旧版本);CSV 分「服药记录 / 症状与随笔」两段,方便给医生看

## 技术

- Kotlin + Jetpack Compose(Material 3),单 `:app` 模块,手动依赖注入
- Room 存储(带版本迁移);排程引擎为纯 Kotlin 领域代码并注入 `Clock`,便于单测
- `AlarmManager` 精确闹钟 + WorkManager 巡检;SAF 读写文件,导出全程无需网络
- **不申请 `INTERNET` 权限**

## 构建与安装

```bash
./gradlew :app:testDebugUnitTest     # 单元测试 + Robolectric 数据库/备份测试
./gradlew :app:installDebug          # 装调试包
./gradlew :app:installRelease        # 装正式包(需本地 keystore,见下)
```

正式包需要本地签名(签名材料不入库):

- keystore 与凭据放在仓库外(`~/.android-keys/`、`~/.gradle/gradle.properties`)
- 详细步骤与发布前检查清单见 [`docs/release.md`](docs/release.md)

## 文档

- [`docs/plan.md`](docs/plan.md) — 设计决策、领域模型、调度语义、里程碑(M1–M6)
- [`docs/release.md`](docs/release.md) — 签名、R8、发布检查清单
