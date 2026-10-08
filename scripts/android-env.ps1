$ErrorActionPreference = 'Stop'
$env:ANDROID_HOME = 'D:\DevTools\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'D:\DevTools\Android\Sdk'
$env:ANDROID_USER_HOME = 'D:\DevTools\Android\UserHome'
$env:ANDROID_AVD_HOME = 'D:\DevTools\Android\UserHome\avd'
$env:ADB_VENDOR_KEYS = 'D:\DevTools\Android\UserHome\adbkey'
$env:JAVA_HOME = 'D:\DevTools\Java\jdk-17.0.20.101-hotspot'

# Use the local SDK/cache when the previous development tools were removed.
$localTools = Join-Path $PSScriptRoot '..\.tools'
if (-not (Test-Path -LiteralPath $env:ANDROID_HOME)) {
    $env:ANDROID_HOME = [IO.Path]::GetFullPath((Join-Path $localTools 'widget-fix\sdk'))
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:ANDROID_USER_HOME = [IO.Path]::GetFullPath((Join-Path $localTools 'android-user-home'))
    $env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
    $env:ADB_VENDOR_KEYS = Join-Path $env:ANDROID_USER_HOME 'adbkey'
}
if (-not (Test-Path -LiteralPath $env:JAVA_HOME)) {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2'
}
if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME) -or -not (Test-Path -LiteralPath $env:GRADLE_USER_HOME)) {
    $env:GRADLE_USER_HOME = [IO.Path]::GetFullPath((Join-Path $localTools 'gradle-cache'))
}

