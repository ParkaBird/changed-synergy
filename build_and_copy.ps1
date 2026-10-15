[CmdletBinding()]
param(
    [string]$ModsDirectory = $env:SYNERGY_MODS_DIR,
    [string]$JavaHome = $env:JAVA_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $JavaHome = Join-Path $env:APPDATA ".minecraft\runtime\java-runtime-beta"
}
if ([string]::IsNullOrWhiteSpace($ModsDirectory)) {
    throw "Pass -ModsDirectory or set SYNERGY_MODS_DIR to the target profile's mods directory."
}

$javaExecutable = Join-Path $JavaHome "bin\java.exe"

if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw "Java 17 runtime was not found at $javaExecutable"
}

$env:JAVA_HOME = $JavaHome
Push-Location $projectRoot
try {
    & ".\gradlew.bat" clean build --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $artifacts = @(
        Get-ChildItem -LiteralPath (Join-Path $projectRoot "build\libs") -File -Filter "*.jar" |
            Where-Object {
                $_.Name -notmatch "-sources\.jar$" -and
                $_.Name -notmatch "-javadoc\.jar$"
            }
    )
    if ($artifacts.Count -ne 1) {
        throw "Expected one deployable jar, found $($artifacts.Count)."
    }

    New-Item -ItemType Directory -Force -Path $modsDirectory | Out-Null
    & (Join-Path $projectRoot 'tools/install_test_build.ps1') `
        -JarPath $artifacts[0].FullName -ModsDirectory $ModsDirectory -SkipBackup

    Write-Host "Build and copy successful."
    Write-Host "Artifact: $($artifacts[0].FullName)"
    Write-Host "SHA-256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $artifacts[0].FullName).Hash)"
}
finally {
    Pop-Location
}
