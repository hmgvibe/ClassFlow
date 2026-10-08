param(
    [ValidateSet('cloud', 'offline')][string]$Flavor = 'cloud',
    [string]$MappingPath = (Join-Path $PSScriptRoot "..\android\app\build\outputs\mapping\$($Flavor)Release\mapping.txt")
)

$ErrorActionPreference = 'Stop'
$mapping = Get-Content -LiteralPath $MappingPath -Raw
foreach ($widgetClass in 'com.ray.classflow.widget.ClassFlowWidget', 'com.ray.classflow.widget.TimetableWidget') {
    $expectedMapping = '(?m)^' + [regex]::Escape($widgetClass) + ' -> ' + [regex]::Escape($widgetClass) + ':\r?$'
    if ($mapping -notmatch $expectedMapping) {
        throw "Widget identity was renamed or merged by R8: $widgetClass. Glance requires separate, stable widget classes."
    }
}
Write-Host 'Release widget identities are stable and distinct.'
