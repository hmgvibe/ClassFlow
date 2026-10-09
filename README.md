# ClassFlow

ClassFlow 是一套以 Nextcloud 為同步中心的個人課表與校園日程管理工具。專案包含 Android App 與 Nextcloud 35 App，兩端都能管理固定每週課表、作業、考試及活動。

## 功能

- 課程與固定每週課表
- 作業、考試、活動與其他日程
- 學習計劃：五日日期欄、上午／下午／晚上分組，可連結課程、作業或考試並查看時間提示
- 日程連結多個課堂
- Android 離線資料庫與背景同步
- 獨立 Android 離線版：可與原版同時安裝，資料互不共用，不需要 Nextcloud 或網路權限
- Nextcloud Login Flow v2
- 本機提醒、搜尋、篩選與桌面小工具
- Nextcloud 網頁端完整管理
- 淺色與深色 Android UI
- Android 與網頁端支援簡體／繁體中文，跟隨系統或 Nextcloud 使用者語言

宣傳影片與真實操作錄製、重新生成方式見 [影片製作說明](docs/promo-video.md)。

學習計劃的時間需自行安排，連結對象的時間僅供參考。2.0.0 的資料遷移、升級與驗證方式見 [學習計劃說明](docs/study-plans.md)。

## Android 命令列開發

本機已配置 `ClassFlow_API_36`。不需要 Android Studio：

```powershell
.\scripts\start-emulator.ps1
.\scripts\install-android.ps1
```

只建置與測試：

```powershell
.\gradlew.bat :android:app:testCloudDebugUnitTest :android:app:lintCloudDebug :android:app:assembleCloudDebug
```

獨立離線版使用 `offline` 變體，開發安裝可執行
`.\scripts\install-android.ps1 -Flavor offline`。兩版的識別、驗證與日後打包方式見
[離線版說明](docs/offline-edition.md)。

## Nextcloud App

Nextcloud App 位於 `nextcloud/classflow`，目標版本為 Nextcloud 35。建置網頁資產：

```powershell
.\scripts\build-nextcloud.ps1
```

測試伺服器可使用 `deploy/compose.test.yml`。先由 `.env.example` 建立伺服器端 `.env` 並更換所有密碼，再執行：

```bash
docker compose --env-file .env -f compose.test.yml up -d
docker compose --env-file .env -f compose.test.yml exec -u www-data app php occ app:enable classflow
```

Android 模擬器連到 Windows 本機服務時使用 `http://10.0.2.2:18088`；獨立伺服器測試環境應使用 HTTPS 網域。

## 專案結構

- `android/app`：Kotlin、Compose、Room、WorkManager、Glance
- `nextcloud/classflow`：PHP OCS API、資料庫 Migration、Vue 3 網頁端
- `deploy`：隔離的 Nextcloud 35 測試環境
- `scripts`：命令列建置、安裝與打包腳本

## 授權

AGPL-3.0-or-later

