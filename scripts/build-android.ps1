. "$PSScriptRoot\android-env.ps1"
& "$PSScriptRoot\..\gradlew.bat" :android:app:assembleDebug @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

