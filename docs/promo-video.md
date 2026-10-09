# ClassFlow 宣传视频

成片：`artifacts/promo/ClassFlow-promo-1080p.mp4`。60 秒、1920×1080、30 fps，
H.264 / yuv420p / AAC，含烧录简体字幕、独立 SRT、封面与原创配乐。

## 素材与真实性

Android 素材来自专用 `ClassFlow_Promo` 模拟器上的正式 `MainActivity`、真实 Room
数据库及桌面小组件。录制测试先准备虚构课程，再通过真实界面操作切换星期、
完成作业、输入计划、关联考试并保存；测试还检查保存后的数据库内容。
录制脚本仅接受端口 5580 上名称为 `ClassFlow_Promo` 的模拟器。

网页素材来自生产 Vue 组件。`scripts/promo/preview.mjs` 只在 `127.0.0.1:4179`
提供隔离的内存示例 API，不是 Nextcloud 服务器。视频展示两端界面，
不展示或宣称已经验证跨端同步成功。原始录屏和网页截图流保存在 `raw/`，
网页截图带采集时间戳。成片只裁剪系统状态栏、适度加速和添加宣传图文；
不替换界面中的文字、按钮或操作结果。

## 再次生成

准备 Python（Pillow、numpy）、FFmpeg、FFprobe 与微软雅黑字体，运行：

```powershell
python scripts/promo/render.py --ffmpeg <ffmpeg.exe> --ffprobe <ffprobe.exe>
```

渲染器使用 `artifacts/promo/raw` 下保留的原始素材，重建封面、配乐、SRT、成片、
场景联系表、FFprobe 信息与素材说明。`original-music.wav` 为脚本按 100 BPM
合成的原创器乐，不使用第三方曲目。

交付目录中的 `source/render.py` 和 `source/classflow-icon.png` 也可直接使用：
该副本会自动读取同目录上一级的 `raw/`，不依赖 Android 源码目录。
`source/requirements.txt` 列出 Python 依赖；使用 `--font` 可更换本地中文字体。
素材中的 `*-touches.json` 记录真实屏幕位置和时间，供点击提示与结果放大使用。

重新拍摄 Android：启动独立 AVD，再执行
`scripts/promo/record-android.ps1 -Flavor cloud` 和 `-Flavor offline`。
录制会把这个测试应用的语言设为简体并写入示例数据，不能用于个人设备。
`-PinWidget` 调用 Android 正式小组件固定接口；在专用桌面确认添加后录制桌面。

重新拍摄网页：先执行 `scripts/build-nextcloud.ps1`，再运行
`node scripts/promo/preview.mjs`；在隔离浏览器中录制课表、日程和学习计划切换。
内存示例不含账号和私人数据，不可用于生产部署。

## 语言支持

Android 的默认资源保留繁体；`values-b+zh+Hans` 提供简体，`values-b+zh+Hant`
提供繁体，跟随系统语言。日期显示跟随当前配置。语言变化时重新更新提醒频道
和已放置的小组件。两种安装版本共享文案资源，离线版的应用名另有对应翻译。
验证错误使用稳定的 `UiText` 消息标识，展示时解析为当前语言。
日期及系统日期选择器使用同一简繁体规则；其他语言回退繁体。

网页端通过 `@nextcloud/l10n` 读取 Nextcloud 用户语言并加载简体文案。
简体中文地区与 Hans 脚本采用简体，繁体及不支持的语言回退到原有繁体。
所有翻译只作用于应用文案，不转换用户填写的课程、笔记或标题。
API、数据库结构、存储枚举、包名和版本号保持兼容。

网页服务端已知验证错误由前端按现有错误标识翻译，API 响应保持原样。
构建源集已为 AGP 内置 Kotlin 显式包含离线版专属设备测试：
参见 [Android 官方迁移说明](https://developer.android.com/build/migrate-to-built-in-kotlin)。
