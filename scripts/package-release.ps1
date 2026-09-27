$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$releaseDirectory = Join-Path $repositoryRoot 'build\release'
$versionMatch = Select-String -Path (Join-Path $repositoryRoot 'android\app\build.gradle.kts') -Pattern 'versionName\s*=\s*"([^"]+)"'
if (-not $versionMatch) {
    throw 'Unable to determine versionName from android/app/build.gradle.kts'
}
$versionName = $versionMatch.Matches[0].Groups[1].Value

if (Test-Path $releaseDirectory) {
    Remove-Item -LiteralPath $releaseDirectory -Recurse -Force
}

& "$PSScriptRoot\package-android-release.ps1" -OutputDirectory $releaseDirectory
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& "$PSScriptRoot\package-nextcloud.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$nextcloudPackage = Join-Path $repositoryRoot "build\ClassFlow-Nextcloud-v$versionName.zip"
Copy-Item -LiteralPath $nextcloudPackage -Destination $releaseDirectory -Force

$artifacts = Get-ChildItem -LiteralPath $releaseDirectory -File |
    Where-Object { $_.Extension -in '.apk', '.zip' } |
    Sort-Object Name
$checksumLines = foreach ($artifact in $artifacts) {
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $artifact.FullName
    "$($hash.Hash.ToLowerInvariant())  $($artifact.Name)"
}
$checksumPath = Join-Path $releaseDirectory 'SHA256SUMS.txt'
[IO.File]::WriteAllLines($checksumPath, $checksumLines, [Text.UTF8Encoding]::new($false))

Write-Host "Release files:"
Get-ChildItem -LiteralPath $releaseDirectory -File | Select-Object Name, Length, LastWriteTime

