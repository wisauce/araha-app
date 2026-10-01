$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
Push-Location $project
try {
    $sdkPath = Join-Path $project '.tools/android-sdk'
    if (Test-Path $sdkPath) { $env:ANDROID_HOME = $sdkPath }
    & (Join-Path $project 'gradlew.bat') -g (Join-Path $project '.tools/gradle-home') --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
    if ($LASTEXITCODE -ne 0) { throw 'Build checks failed.' }
    Copy-Item -LiteralPath (Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk') -Destination (Join-Path $project 'delivery/ARAHA-debug.apk') -Force
} finally { Pop-Location }
