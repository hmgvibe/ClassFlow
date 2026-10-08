param([ValidateSet('cloud', 'offline')][string]$Flavor = 'cloud')

. "$PSScriptRoot\android-env.ps1"
$variant = [Globalization.CultureInfo]::InvariantCulture.TextInfo.ToTitleCase($Flavor) + 'Debug'
& "$PSScriptRoot\..\gradlew.bat" ":android:app:assemble$variant" @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

