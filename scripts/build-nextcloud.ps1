$ErrorActionPreference = 'Stop'
$appDir = Join-Path $PSScriptRoot '..\nextcloud\classflow'
$nodeDir = 'D:\DevTools\Node\node-v24.20.0-win-x64'
$env:Path = "$nodeDir;$env:Path"

Push-Location $appDir
try {
    & "$nodeDir\corepack.cmd" pnpm install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & "$nodeDir\corepack.cmd" pnpm lint
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & "$nodeDir\corepack.cmd" pnpm typecheck
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & "$nodeDir\corepack.cmd" pnpm build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

