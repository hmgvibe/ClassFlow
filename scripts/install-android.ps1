param([ValidateSet('cloud', 'offline')][string]$Flavor = 'cloud')

. "$PSScriptRoot\android-env.ps1"
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$variant = [Globalization.CultureInfo]::InvariantCulture.TextInfo.ToTitleCase($Flavor) + 'Debug'
$applicationId = if ($Flavor -eq 'offline') { 'com.ray.classflow.offline.debug' } else { 'com.ray.classflow.debug' }
& "$PSScriptRoot\..\gradlew.bat" ":android:app:install$variant"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb shell monkey -p $applicationId -c android.intent.category.LAUNCHER 1 | Out-Null
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "ClassFlow ($Flavor) 已安裝並啟動。"

