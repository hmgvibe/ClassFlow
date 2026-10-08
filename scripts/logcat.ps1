param([ValidateSet('cloud', 'offline')][string]$Flavor = 'cloud')

. "$PSScriptRoot\android-env.ps1"
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$applicationId = if ($Flavor -eq 'offline') { 'com.ray.classflow.offline.debug' } else { 'com.ray.classflow.debug' }
& $adb logcat --pid=(& $adb shell pidof $applicationId)

