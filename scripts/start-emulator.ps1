. "$PSScriptRoot\android-env.ps1"

$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'

if (-not (& $emulator -list-avds | Select-String '^ClassFlow_API_36$')) {
    throw '找不到 ClassFlow_API_36，請先建立 Android 虛擬裝置。'
}

if (-not (Get-Process -Name 'qemu-system-x86_64' -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath $emulator -ArgumentList @('-avd', 'ClassFlow_API_36', '-gpu', 'auto')
}

& $adb wait-for-device
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    if ((& $adb shell getprop sys.boot_completed 2>$null).Trim() -eq '1') {
        Write-Host 'ClassFlow_API_36 已可使用。'
        exit 0
    }
    Start-Sleep -Seconds 1
}

throw '模擬器未在 90 秒內完成開機。'

