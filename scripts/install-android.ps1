. "$PSScriptRoot\android-env.ps1"
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& "$PSScriptRoot\..\gradlew.bat" :android:app:installDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb shell monkey -p com.ray.classflow.debug -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host 'ClassFlow 已安裝並啟動。'

