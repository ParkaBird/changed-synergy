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
    $destination = Join-Path $modsDirectory $artifacts[0].Name
    $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifacts[0].FullName).Hash

    # Never rewrite a jar in place. Forge keeps an open archive index while the
    # game is running; mutating that file can make it read a mixture of old and
    # new classes. Stage the complete artifact and replace the directory entry
    # in one operation instead.
    $deployId = [Guid]::NewGuid().ToString("N")
    $staged = Join-Path $modsDirectory ($artifacts[0].Name + ".$deployId.staged")
    $backup = Join-Path $modsDirectory ($artifacts[0].Name + ".$deployId.backup")
    try {
        Copy-Item -LiteralPath $artifacts[0].FullName -Destination $staged -Force
        $stagedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $staged).Hash
        if ($sourceHash -ne $stagedHash) {
            throw "Staged jar hash does not match the build artifact."
        }

        if (Test-Path -LiteralPath $destination) {
            [System.IO.File]::Replace($staged, $destination, $backup, $true)
        }
        else {
            [System.IO.File]::Move($staged, $destination)
        }
    }
    finally {
        if (Test-Path -LiteralPath $staged) {
            Remove-Item -LiteralPath $staged -Force
        }
        if (Test-Path -LiteralPath $backup) {
            Remove-Item -LiteralPath $backup -Force
        }
    }

    $installedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash
    if ($sourceHash -ne $installedHash) {
        throw "Installed jar hash does not match the build artifact."
    }

    Write-Host "Build and copy successful."
    Write-Host "Artifact: $($artifacts[0].FullName)"
    Write-Host "Installed: $destination"
    Write-Host "SHA-256: $sourceHash"
}
finally {
    Pop-Location
}
