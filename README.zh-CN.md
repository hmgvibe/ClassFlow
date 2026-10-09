# ClassFlow

[繁體中文](README.md) | 简体中文

ClassFlow 是一套以 Nextcloud 为同步中心的个人课表与校园日程管理工具。项目包含 Android 应用与 Nextcloud 35 应用，两端都能管理固定每周课表、作业、考试及活动。

## 功能

- 课程与固定每周课表
- 作业、考试、活动与其他日程
- 学习计划：五日日期栏、上午／下午／晚上分组，可关联课程、作业或考试并查看时间提示
- 日程关联多个课堂
- Android 离线数据库与后台同步
- 独立 Android 离线版：可与同步版同时安装，数据互不共用，无需 Nextcloud 或网络权限
- Nextcloud Login Flow v2
- 本地提醒、搜索、筛选与桌面小组件
- Nextcloud 网页端完整管理
- 浅色与深色 Android 界面
- Android 与网页端支持简体／繁体中文，跟随系统或 Nextcloud 用户语言

宣传视频与真实操作录制、重新生成方式见 [视频制作说明](docs/promo-video.md)。

学习计划的时间需自行安排，关联对象的时间仅供参考。2.0.0 的数据迁移、升级与验证方式见 [学习计划说明](docs/study-plans.md)。

## Android 命令行开发

本地已配置 `ClassFlow_API_36`。无需 Android Studio：

```powershell
.\scripts\start-emulator.ps1
.\scripts\install-android.ps1
```

仅构建与测试：

```powershell
.\gradlew.bat :android:app:testCloudDebugUnitTest :android:app:lintCloudDebug :android:app:assembleCloudDebug
```

独立离线版使用 `offline` 变体，开发安装可执行
`.\scripts\install-android.ps1 -Flavor offline`。两版的标识、验证与打包方式见
[离线版说明](docs/offline-edition.md)。

## Nextcloud 应用

Nextcloud 应用位于 `nextcloud/classflow`，目标版本为 Nextcloud 35。构建网页资源：

```powershell
.\scripts\build-nextcloud.ps1
```

测试服务器可使用 `deploy/compose.test.yml`。先由 `.env.example` 创建服务器端 `.env` 并更换所有密码，再执行：

```bash
docker compose --env-file .env -f compose.test.yml up -d
docker compose --env-file .env -f compose.test.yml exec -u www-data app php occ app:enable classflow
```

Android 模拟器连接 Windows 本地服务时使用 `http://10.0.2.2:18088`；独立服务器测试环境应使用 HTTPS 域名。

## 项目结构

- `android/app`：Kotlin、Compose、Room、WorkManager、Glance
- `nextcloud/classflow`：PHP OCS API、数据库迁移、Vue 3 网页端
- `deploy`：隔离的 Nextcloud 35 测试环境
- `scripts`：命令行构建、安装与打包脚本

## 许可证

AGPL-3.0-or-later
