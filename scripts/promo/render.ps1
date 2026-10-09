param([string]$Python = '', [string]$FFmpeg = '', [string]$FFprobe = '')
$ErrorActionPreference = 'Stop'
if (-not $Python) {
    $bundledPython = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
    $Python = if (Test-Path -LiteralPath $bundledPython) { $bundledPython } else { 'python' }
}
if (-not $FFmpeg) {
    $installedFFmpeg = 'C:/Program Files/Android_Dex_Windows/ffmpeg.exe'
    $FFmpeg = if (Test-Path -LiteralPath $installedFFmpeg) { $installedFFmpeg } else { 'ffmpeg' }
}
if (-not $FFprobe) {
    $ffmpegDirectory = Split-Path -Parent $FFmpeg
    $sibling = if ($ffmpegDirectory) { Join-Path $ffmpegDirectory 'ffprobe.exe' } else { '' }
    $FFprobe = if ($sibling -and (Test-Path -LiteralPath $sibling)) { $sibling } else { 'ffprobe' }
}
& $Python (Join-Path $PSScriptRoot 'render.py') --ffmpeg $FFmpeg --ffprobe $FFprobe
if ($LASTEXITCODE -ne 0) { throw 'Rendering failed' }
& $Python (Join-Path $PSScriptRoot 'verify.py') --ffmpeg $FFmpeg --ffprobe $FFprobe
if ($LASTEXITCODE -ne 0) { throw 'Encoding verification failed' }
