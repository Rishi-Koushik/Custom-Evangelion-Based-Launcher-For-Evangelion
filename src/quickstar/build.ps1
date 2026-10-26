# targetSdk 33 on purpose: Android 14+ only lets apps with targetSdk <= 33 read non-public settings keys
# such as sysui_qs_tiles, which this app needs.
# Builds Quickstar without Gradle: aapt2 -> javac -> d8 -> aapt add -> zipalign -> apksigner.
# Usage:  .\build.ps1            build only
#         .\build.ps1 -Install   build, install over USB, grant secure-settings permissions
param([switch]$Install)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$bt = Join-Path $sdk 'build-tools\35.0.0'
$jar = Join-Path $sdk 'platforms\android-35\android.jar'
$adb = 'adb'   # must be on PATH
$pkg = 'dev.rishi.quickstar'
$out = Join-Path $root 'build'

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null

function Run($exe, $argList) {
    $ErrorActionPreference = 'Continue'   # native stderr notes (e.g. javac deprecation) are not failures
    & $exe @argList 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "failed: $exe $($argList -join ' ')" }
}

Run "$bt\aapt2.exe" @('compile', '--dir', "$root\res", '-o', "$out\res.zip")
Run "$bt\aapt2.exe" @('link', '-o', "$out\base.apk", '-I', $jar, '--manifest', "$root\AndroidManifest.xml",
    '--java', "$out\gen", '--min-sdk-version', '33', '--target-sdk-version', '33',
    '--version-code', '1', '--version-name', '0.1', "$out\res.zip")

$sources = @(Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object FullName) +
           @(Get-ChildItem "$out\gen" -Recurse -Filter *.java | ForEach-Object FullName)
$list = "$out\sources.txt"
[IO.File]::WriteAllLines($list, $sources, (New-Object Text.UTF8Encoding $false))
Run 'javac' @('-nowarn', '-encoding', 'UTF-8', '--release', '8', '-cp', $jar, '-d', "$out\classes", "@$list")

$classes = @(Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName)
Run "$bt\d8.bat" (@('--lib', $jar, '--min-api', '33', '--output', "$out\dex") + $classes)

Push-Location "$out\dex"
Run "$bt\aapt.exe" @('add', "$out\base.apk", 'classes.dex')
Pop-Location

Run "$bt\zipalign.exe" @('-f', '4', "$out\base.apk", "$out\aligned.apk")

$ks = Join-Path $root 'debug.jks'
if (-not (Test-Path $ks)) {
    $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    Run $keytool @('-genkeypair', '-keystore', $ks, '-storepass', 'android', '-keypass', 'android', '-alias', 'quickstar',
        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '36500', '-dname', 'CN=Quickstar')
}
$apk = Join-Path $root 'quickstar.apk'
Run "$bt\apksigner.bat" @('sign', '--ks', $ks, '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', $apk, "$out\aligned.apk")
Write-Host "Built $apk ($([int]((Get-Item $apk).Length / 1024)) KB)"

if ($Install) {
    Run $adb @('install', '-r', $apk)
    Run $adb @('shell', 'pm', 'grant', $pkg, 'android.permission.WRITE_SECURE_SETTINGS')
    Run $adb @('shell', 'appops', 'set', $pkg, 'WRITE_SETTINGS', 'allow')
    Write-Host 'Installed and permissions granted.'
}

