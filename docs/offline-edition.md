# ClassFlow 離線版

同一份 Android 程式碼提供 `cloud` 與 `offline` 兩個建置變體，避免維護兩份功能。
它們是不同 App，可同時安裝；Android 的 App 沙盒分開儲存資料庫、設定、提醒與小組件。

| 版本 | 正式版套件名稱 | 桌面名稱 |
| --- | --- | --- |
| 原版 (`cloud`) | `com.ray.classflow` | ClassFlow |
| 離線版 (`offline`) | `com.ray.classflow.offline` | ClassFlow 離線版 |

Debug 版的套件名稱另加 `.debug`。原版的正式套件名稱、Nextcloud 同步與既有資料保持不變。

離線版保留課表、日程、學習計劃、課堂連結、本機提醒與兩種小組件；不顯示登入、同步或
衝突操作，不要求 INTERNET／ACCESS_NETWORK_STATE，也不排程同步工作或建立同步佇列。
它不是原版的「暫時離線」選項，不能連接 Nextcloud，也不會自動匯入原版資料。
資料只存在本機，已關閉 Android 備份；卸載或清除資料將失去本機內容，目前沒有匯出功能。

## 開發與驗證

```powershell
.\scripts\install-android.ps1 -Flavor offline
.\scripts\logcat.ps1 -Flavor offline
.\gradlew.bat :android:app:testOfflineDebugUnitTest :android:app:lintOfflineDebug :android:app:compileOfflineDebugKotlin
```

`build-android.ps1`／`install-android.ps1`／`logcat.ps1` 預設仍操作原版 (`cloud`)。
需驗證原版時使用 `testCloudDebugUnitTest`、`lintCloudDebug`、`compileCloudDebugKotlin`。

離線版的實機測試位於 `src/offlineAndroidTest`，覆蓋無網路權限、無 Android 備份、直接進入
主頁、本機 CRUD、不建立同步佇列及同步 worker；執行 `connectedOfflineDebugAndroidTest`
需要已連接的模擬器／手機，單元測試與編譯不等於實機驗證。

## 正式打包

```powershell
.\scripts\package-android-release.ps1 -Flavor cloud
.\scripts\package-android-release.ps1 -Flavor offline
```

輸出分別是 `ClassFlow-v<VERSION>.apk` 與 `ClassFlow-Offline-v<VERSION>.apk`，不會互相覆寫。
離線版不需要 Nextcloud 安裝包。執行 `scripts/package-release.ps1` 可一次打包原版、離線版、
Nextcloud App 及 SHA-256 校驗檔。兩份 Android APK 的正式版本皆為 2.0.0。
