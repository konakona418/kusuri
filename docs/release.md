# 发布准备

## 版本
- `versionCode` / `versionName` 位于 `app/build.gradle.kts`;每次对外发布必须递增 `versionCode`。
- 当前:`versionCode = 1`、`versionName = "1.0"`。

## 签名(本机已配置)
- keystore:`~/.android-keys/kusuri-release.jks`(**仓库外**,已 `chmod 600`)
- 凭据:`~/.gradle/gradle.properties` 中的 `kusuriStoreFile` / `kusuriStorePassword` / `kusuriKeyAlias` / `kusuriKeyPassword`
- `app/build.gradle.kts` 里**有这些属性才启用** release 签名;没有时仍可构建未签名包(CI 友好)
- 这两个文件请自行备份:**丢失后无法用同一签名升级,只能卸载重装**

首次在别的机器上重建:

1. 生成 keystore(别名 `kusuri`,RSA 4096,10000 天):
   ```
   keytool -genkeypair -v -keystore ~/.android-keys/kusuri-release.jks \
     -alias kusuri -keyalg RSA -keysize 4096 -validity 10000
   ```
2. 把四个 `kusuri*` 属性写进 `~/.gradle/gradle.properties`(不入库)。
3. `./gradlew :app:assembleRelease` → 产物为 `app-release.apk`(已签名),用
   `apksigner verify --print-certs` 校验。

> **debug 与 release 签名不同,不能互相覆盖安装。** 从 debug 切到 release 必须先卸载(会清数据,先用"导出完整备份(JSON)"保命);之后同一签名的 release 之间可以直接升级。

## 日常装机
- 正式包(保留数据):`./gradlew :app:installRelease`
- 调试包:`./gradlew :app:installDebug`(若手机上装的是正式包,会因签名不同失败,需先卸载)

## R8
- release 已开启 `optimization { enable = true }`(AGP 9 DSL)。
- Room / Compose / WorkManager 自带 consumer rules;项目特定规则写在 `app/src/main/keepRules/rules.keep`。
- R8 只能靠真机冒烟验证:每次发布前跑一遍下面的清单。

## 发布前检查清单
- [ ] 权限清单符合预期:`aapt dump permissions app-release.apk` 应只有通知/闹钟/开机/震动/相机/网络
- [ ] 真机:通知权限、精确闹钟、电池优化向导可走完
- [ ] 真机:切换提醒等级(静默/只震动/响亮/横幅)→ 每条都点一次「试一下」,响铃/震动/横幅与档位一致,系统设置里四条渠道也各就各位
- [ ] 真机:新建药物 → 提醒 → 通知三动作(已服用/稍后/跳过)
- [ ] 真机:重启手机后提醒仍有效
- [ ] 导出 JSON → 清数据/换机 → 导入,数据一致
- [ ] 导出 CSV 可在表格软件中打开(中文表头、CRLF)
- [ ] 局域网导出:`uv run tools/kusuri-receive.py` → 手机扫码发送 JSON 与 CSV → 电脑落盘路径与 sha256 一致,且落盘文件能导回手机
- [ ] 局域网导出:相机权限拒绝后,手输路径仍可用
- [ ] `versionCode` 已递增
