. "$PSScriptRoot\android-env.ps1"
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb logcat --pid=(& $adb shell pidof com.ray.classflow.debug)

