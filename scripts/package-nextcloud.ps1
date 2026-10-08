$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\build-nextcloud.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $projectRoot 'nextcloud\classflow'
$staging = Join-Path $projectRoot 'build\nextcloud-package\classflow'
$appInfoPath = Join-Path $source 'appinfo\info.xml'
[xml]$appInfo = Get-Content -LiteralPath $appInfoPath
$versionName = [string]$appInfo.info.version
if ([string]::IsNullOrWhiteSpace($versionName)) {
    throw "Unable to determine the Nextcloud app version from $appInfoPath"
}
$archive = Join-Path $projectRoot "build\ClassFlow-Nextcloud-v$versionName.zip"

if (Test-Path $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null
Copy-Item -Path (Join-Path $source '*') -Destination $staging -Recurse -Force -Exclude @('node_modules', 'src', 'tests', 'package.json', 'pnpm-lock.yaml', 'tsconfig.json', 'vite.config.ts', 'eslint.config.js')
if (Test-Path $archive) { Remove-Item -LiteralPath $archive -Force }
Compress-Archive -Path $staging -DestinationPath $archive -CompressionLevel Optimal
Write-Host "Created $archive"

