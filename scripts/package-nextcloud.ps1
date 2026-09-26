$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\build-nextcloud.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $projectRoot 'nextcloud\classflow'
$staging = Join-Path $projectRoot 'build\nextcloud-package\classflow'
$archive = Join-Path $projectRoot 'build\ClassFlow-Nextcloud-v0.1.0.zip'

if (Test-Path $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null
Copy-Item -Path (Join-Path $source '*') -Destination $staging -Recurse -Force -Exclude @('node_modules', 'src', 'package.json', 'pnpm-lock.yaml', 'tsconfig.json', 'vite.config.ts', 'eslint.config.js')
if (Test-Path $archive) { Remove-Item -LiteralPath $archive -Force }
Compress-Archive -Path $staging -DestinationPath $archive -CompressionLevel Optimal
Write-Host "Created $archive"

