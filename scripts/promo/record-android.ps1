param(
    [ValidateSet('cloud', 'offline')][string]$Flavor = 'cloud',
    [string]$Adb = '',
    [switch]$PinWidget
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $repoRoot 'scripts/android-env.ps1')
if (-not $Adb) { $Adb = Join-Path $repoRoot '.tools/promo-sdk/platform-tools/adb.exe' }
$serial = 'emulator-5580'
$deviceName = & $Adb -s $serial emu avd name
if ($LASTEXITCODE -ne 0 -or $deviceName -notcontains 'ClassFlow_Promo') {
    throw 'Only the disposable ClassFlow_Promo AVD on port 5580 may be recorded.'
}
$variant = [Globalization.CultureInfo]::InvariantCulture.TextInfo.ToTitleCase($Flavor)
$package = if ($Flavor -eq 'offline') { 'com.ray.classflow.offline.debug' } else { 'com.ray.classflow.debug' }
Push-Location $repoRoot
try {
    & ./gradlew.bat ":android:app:assemble${variant}Debug" ":android:app:assemble${variant}DebugAndroidTest"
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    & $Adb -s $serial install -r "android/app/build/outputs/apk/$Flavor/debug/app-$Flavor-debug.apk"
    if ($LASTEXITCODE -ne 0) { throw 'App installation failed' }
    & $Adb -s $serial install -r "android/app/build/outputs/apk/androidTest/$Flavor/debug/app-$Flavor-debug-androidTest.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Test installation failed' }
    & $Adb -s $serial shell pm grant $package android.permission.POST_NOTIFICATIONS
    $method = if ($PinWidget) { 'pinTimetableWidget' } else { 'recordStudentWorkflow' }
    $testArgs = @('shell', 'am', 'instrument', '-w', '-e', 'recordPromo', 'true', '-e', 'class', "com.ray.classflow.PromoRecordingTest#$method")
    if ($PinWidget) { $testArgs += @('-e', 'pinPromoWidget', 'true') }
    $result = & $Adb -s $serial @testArgs "$package.test/androidx.test.runner.AndroidJUnitRunner"
    $output = Join-Path $repoRoot 'artifacts/promo'
    New-Item -ItemType Directory -Force -Path (Join-Path $output 'raw') | Out-Null
    $result | Set-Content -Encoding utf8 (Join-Path $output "record-$Flavor-$method.log")
    if (-not ($result -match 'OK \(1 test\)')) { throw 'Recording did not pass; inspect its log.' }
    if (-not $PinWidget) {
        $clips = if ($Flavor -eq 'offline') { @('offline') } else { @('timetable', 'agenda', 'study') }
        foreach ($clip in $clips) {
            & $Adb -s $serial pull "/sdcard/classflow-$clip.mp4" (Join-Path $output "raw/$clip.mp4")
            if ($LASTEXITCODE -ne 0) { throw "Missing recording: $clip" }
            $touches = & $Adb -s $serial shell run-as $package cat "files/promo-$clip-touches.json"
            $touches | Set-Content -Encoding utf8 (Join-Path $output "raw/$clip-touches.json")
        }
    }
} finally { Pop-Location }
