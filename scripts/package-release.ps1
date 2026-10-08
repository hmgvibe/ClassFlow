$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$releaseDirectory = Join-Path $repositoryRoot 'build\release'
$versionMatch = Select-String -Path (Join-Path $repositoryRoot 'android\app\build.gradle.kts') -Pattern 'versionName\s*=\s*"([^"]+)"'
if (-not $versionMatch) {
    throw 'Unable to determine versionName from android/app/build.gradle.kts'
}
$versionName = $versionMatch.Matches[0].Groups[1].Value

New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null

& "$PSScriptRoot\package-android-release.ps1" -Flavor cloud -OutputDirectory $releaseDirectory
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& "$PSScriptRoot\package-android-release.ps1" -Flavor offline -OutputDirectory $releaseDirectory
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& "$PSScriptRoot\package-nextcloud.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$nextcloudPackage = Join-Path $repositoryRoot "build\ClassFlow-Nextcloud-v$versionName.zip"
Copy-Item -LiteralPath $nextcloudPackage -Destination $releaseDirectory -Force

$artifactNames = @("ClassFlow-v$versionName.apk", "ClassFlow-Offline-v$versionName.apk", "ClassFlow-Nextcloud-v$versionName.zip")
$artifacts = $artifactNames | ForEach-Object { Get-Item -LiteralPath (Join-Path $releaseDirectory $_) } |
    Sort-Object Name
$checksumLines = foreach ($artifact in $artifacts) {
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $artifact.FullName
    "$($hash.Hash.ToLowerInvariant())  $($artifact.Name)"
}
$checksumPath = Join-Path $releaseDirectory 'SHA256SUMS.txt'
[IO.File]::WriteAllLines($checksumPath, $checksumLines, [Text.UTF8Encoding]::new($false))

Write-Host "Release files:"
Get-ChildItem -LiteralPath $releaseDirectory -File | Select-Object Name, Length, LastWriteTime

