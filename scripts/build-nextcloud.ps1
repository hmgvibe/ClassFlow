$ErrorActionPreference = 'Stop'
$appDir = Join-Path $PSScriptRoot '..\nextcloud\classflow'
$nodeDir = 'D:\DevTools\Node\node-v24.20.0-win-x64'
if (Test-Path -LiteralPath $nodeDir) { $env:Path = "$nodeDir;$env:Path" }
$env:CI = 'true'

Push-Location $appDir
try {
    if (-not (Test-Path -LiteralPath 'node_modules/eslint/bin/eslint.js')) {
        & pnpm install --frozen-lockfile
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    & node node_modules/eslint/bin/eslint.js src --max-warnings=0
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & node --test tests/study.test.mjs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & node node_modules/vite/bin/vite.js build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

