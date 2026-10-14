param(
    [Parameter(Mandatory = $true)][string]$JarPath,
    [Parameter(Mandatory = $true)][string]$ModsDirectory,
    [switch]$SkipBackup
)

$ErrorActionPreference = 'Stop'
$taskSource = (Resolve-Path -LiteralPath $JarPath).Path
$taskMods = (Resolve-Path -LiteralPath $ModsDirectory).Path
if (!(Test-Path -LiteralPath $taskMods -PathType Container) -or
    [IO.Path]::GetFileName($taskSource) -notmatch '^ChangedSynergy-.+-all\.jar$') {
    throw 'Provide a Synergy release JAR and an existing mods directory.'
}
$taskRunning = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -in @('java.exe', 'javaw.exe') -and
    ($_.CommandLine -match 'forgeclient|BootstrapLauncher|net\.minecraft\.client|--gameDir' -or
     $_.Name -eq 'javaw.exe')
}
if ($taskRunning) { throw 'Minecraft may still be running. Close it before replacing its mod JAR; no files were changed.' }

$taskTarget = Join-Path $taskMods ([IO.Path]::GetFileName($taskSource))
$taskPending = Join-Path $taskMods ([IO.Path]::GetFileName($taskSource) + '.' + [guid]::NewGuid() + '.pending')
Copy-Item -LiteralPath $taskSource -Destination $taskPending
if (Test-Path -LiteralPath $taskTarget) {
    if ($SkipBackup) {
        [IO.File]::Move($taskPending, $taskTarget, $true)
    } else {
        $taskBackup = Join-Path $taskMods ('.changed-synergy-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
        New-Item -ItemType Directory -Path $taskBackup | Out-Null
        $taskPrevious = Join-Path $taskBackup ([IO.Path]::GetFileName($taskSource))
        [IO.File]::Replace($taskPending, $taskTarget, $taskPrevious)
    }
} else { [IO.File]::Move($taskPending, $taskTarget) }
if ((Get-FileHash -LiteralPath $taskSource).Hash -ne (Get-FileHash -LiteralPath $taskTarget).Hash) {
    throw 'Installed JAR checksum does not match the source.'
}
# A promoted release can have a different filename; leave only one Synergy mod ID.
$taskObsolete = Get-ChildItem -LiteralPath $taskMods -File -Filter 'ChangedSynergy-*-all.jar' |
    Where-Object { $_.FullName -ne $taskTarget }
foreach ($taskOld in $taskObsolete) {
    $taskOldPath = (Resolve-Path -LiteralPath $taskOld.FullName).Path
    if ([IO.Path]::GetDirectoryName($taskOldPath) -ne $taskMods) {
        throw 'Refusing to move a JAR outside the selected mods directory.'
    }
    if ($SkipBackup) {
        Remove-Item -LiteralPath $taskOldPath
    } else {
        if (!$taskBackup) {
            $taskBackup = Join-Path $taskMods ('.changed-synergy-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
            New-Item -ItemType Directory -Path $taskBackup | Out-Null
        }
        Move-Item -LiteralPath $taskOldPath -Destination (Join-Path $taskBackup $taskOld.Name)
    }
}
Write-Output "Installed Synergy build: $taskTarget"
