param(
    [string]$SigningProperties = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.classflow-signing\signing.properties'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\build\release')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

. "$PSScriptRoot\android-env.ps1"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$propertiesPath = (Resolve-Path -LiteralPath $SigningProperties).Path
$properties = @{}

foreach ($line in Get-Content -LiteralPath $propertiesPath) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
        continue
    }

    $parts = $line -split '=', 2
    if ($parts.Count -eq 2) {
        $properties[$parts[0].Trim()] = $parts[1].Trim()
    }
}

foreach ($required in 'storeFile', 'storePassword', 'keyAlias', 'keyPassword') {
    if (-not $properties.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($properties[$required])) {
        throw "Signing property '$required' is missing from $propertiesPath"
    }
}

$keystorePath = (Resolve-Path -LiteralPath $properties.storeFile).Path
$versionMatch = Select-String -Path (Join-Path $repositoryRoot 'android\app\build.gradle.kts') -Pattern 'versionName\s*=\s*"([^"]+)"'
if (-not $versionMatch) {
    throw 'Unable to determine versionName from android/app/build.gradle.kts'
}
$versionName = $versionMatch.Matches[0].Groups[1].Value

$buildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if (-not $buildTools) {
    throw 'Android SDK build-tools were not found.'
}

$zipAlign = Join-Path $buildTools.FullName 'zipalign.exe'
$apkSigner = Join-Path $buildTools.FullName 'apksigner.bat'
$unsignedApk = Join-Path $repositoryRoot 'android\app\build\outputs\apk\release\app-release-unsigned.apk'
$resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
$alignedApk = Join-Path $resolvedOutput "ClassFlow-v$versionName-aligned.apk"
$signedApk = Join-Path $resolvedOutput "ClassFlow-v$versionName.apk"

New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null

$env:CLASSFLOW_STORE_PASSWORD = $properties.storePassword
$env:CLASSFLOW_KEY_PASSWORD = $properties.keyPassword
try {
    & (Join-Path $repositoryRoot 'gradlew.bat') :android:app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }

    & $zipAlign -f -p 4 $unsignedApk $alignedApk
    if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }

    Remove-Item -LiteralPath "$signedApk.idsig" -Force -ErrorAction SilentlyContinue
    & $apkSigner sign `
        --ks $keystorePath `
        --ks-key-alias $properties.keyAlias `
        --ks-pass env:CLASSFLOW_STORE_PASSWORD `
        --key-pass env:CLASSFLOW_KEY_PASSWORD `
        --v4-signing-enabled false `
        --out $signedApk `
        $alignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }

    & $apkSigner verify --verbose --print-certs $signedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
} finally {
    Remove-Item Env:CLASSFLOW_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:CLASSFLOW_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $alignedApk -Force -ErrorAction SilentlyContinue
}

$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $signedApk
Write-Host "Release APK: $signedApk"
Write-Host "SHA-256: $($hash.Hash)"
