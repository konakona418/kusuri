# 发布准备

## 版本
- `versionCode` / `versionName` 位于 `app/build.gradle.kts`;每次对外发布必须递增 `versionCode`。
- 当前:`versionCode = 1`、`versionName = "1.0"`。

## 签名(首次发布前完成一次)
1. 生成 keystore(**不要提交仓库**):
   ```
   keytool -genkeypair -v -keystore kusuri-release.jks -keyalg RSA -keysize 4096 \
     -validity 10000 -alias kusuri
   ```
2. 在 `~/.gradle/gradle.properties`(本机,不入库)写入:
   ```
   kusuriStoreFile=/absolute/path/kusuri-release.jks
   kusuriStorePassword=…
   kusuriKeyAlias=kusuri
   kusuriKeyPassword=…
   ```
3. 在 `app/build.gradle.kts` 中绑定(示例):
   ```kotlin
   android {
       signingConfigs {
           create("release") {
               storeFile = file(providers.gradleProperty("kusuriStoreFile").get())
               storePassword = providers.gradleProperty("kusuriStorePassword").get()
               keyAlias = providers.gradleProperty("kusuriKeyAlias").get()
               keyPassword = providers.gradleProperty("kusuriKeyPassword").get()
           }
       }
       buildTypes {
           release { signingConfig = signingConfigs.getByName("release") }
       }
   }
   ```
4. `./gradlew :app:assembleRelease`,再用 `apksigner verify` 校验产物。

## R8
- release 已开启 `optimization { enable = true }`(AGP 9 DSL)。
- Room / Compose / WorkManager 自带 consumer rules;项目特定规则写在 `app/src/main/keepRules/rules.keep`。
- R8 只能靠真机冒烟验证:每次发布前跑一遍下面的清单。

## 发布前检查清单
- [ ] 不含 `INTERNET` 权限(`aapt dump permissions app-release.apk` 应无 `android.permission.INTERNET`)
- [ ] 真机:通知权限、精确闹钟、电池优化向导可走完
- [ ] 真机:新建药物 → 提醒 → 通知三动作(已服用/稍后/跳过)
- [ ] 真机:重启手机后提醒仍有效
- [ ] 导出 JSON → 清数据/换机 → 导入,数据一致
- [ ] 导出 CSV 可在表格软件中打开(中文表头、CRLF)
- [ ] `versionCode` 已递增
