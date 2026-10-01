$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $project '.tools'
$sdkRoot = Join-Path $toolRoot 'android-sdk'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
$archive = Join-Path $toolRoot 'android-cli.zip'
if (-not (Test-Path (Join-Path $sdkRoot 'cmdline-tools/bin/sdkmanager.bat'))) {
    if (-not (Test-Path $archive)) { Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $archive }
    Expand-Archive -LiteralPath $archive -DestinationPath $sdkRoot -Force
}
$manager = Join-Path $sdkRoot 'cmdline-tools/bin/sdkmanager.bat'
1..20 | ForEach-Object { 'y' } | & $manager "--sdk_root=$sdkRoot" --licenses
if ($LASTEXITCODE -ne 0) { throw 'Android SDK license setup failed.' }
& $manager "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK installation failed.' }
Write-Output "SDK ready: $sdkRoot. Set ANDROID_HOME to this path when building."
