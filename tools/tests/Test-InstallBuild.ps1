# Run with PowerShell 7. Uses a private fake build; never touches a game profile.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$cases = Join-Path $repo ('.codex-tmp/install-regression-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $cases
$success = $false
function Get-CimInstance { @() }
function Assert($condition, $message) { if (!$condition) { throw $message } }
try {
    $fixture = Join-Path $cases 'project'
    $mods = Join-Path $cases 'mods'
    $java = Join-Path $cases 'java'
    $null = New-Item -ItemType Directory -Path "$fixture/tools", "$fixture/build/libs", "$java/bin", $mods
    Copy-Item -LiteralPath (Join-Path $repo 'build_and_copy.ps1') -Destination $fixture
    Copy-Item -LiteralPath (Join-Path $repo 'tools/install_test_build.ps1') -Destination "$fixture/tools"
    Set-Content -LiteralPath "$java/bin/java.exe" -Value 'unused Java path fixture'
    Set-Content -LiteralPath "$fixture/gradlew.bat" -Value '@exit /b 0'
    $jar = "$fixture/build/libs/ChangedSynergy-m1.20.1-vtest-all.jar"
    Set-Content -LiteralPath $jar -Value 'complete new artifact'
    $sourceHash = (Get-FileHash -LiteralPath $jar).Hash
    $installed = Join-Path $mods ([IO.Path]::GetFileName($jar))
    Set-Content -LiteralPath $installed -Value 'previous artifact'
    $previousHash = (Get-FileHash -LiteralPath $installed).Hash
    $install = Join-Path $fixture 'tools/install_test_build.ps1'
    & $install -JarPath $jar -ModsDirectory $mods
    Assert ((Get-FileHash -LiteralPath $installed).Hash -eq $sourceHash) 'Installation changed artifact bytes.'
    $backups = @(Get-ChildItem -LiteralPath "$mods/.changed-synergy-backups" -Recurse -File)
    Assert ($backups.Count -eq 1 -and (Get-FileHash -LiteralPath $backups[0].FullName).Hash -eq $previousHash) 'The previous artifact was not preserved.'
    & $install -JarPath $jar -ModsDirectory $mods -SkipBackup
    Assert (@(Get-ChildItem -LiteralPath "$mods/.changed-synergy-backups" -Recurse -File).Count -eq 1) 'SkipBackup created an extra backup.'
    $obsolete = Join-Path $mods 'ChangedSynergy-m1.20.1-vold-all.jar'
    Copy-Item -LiteralPath $jar -Destination $obsolete
    & $install -JarPath $jar -ModsDirectory $mods -SkipBackup
    Assert (!(Test-Path -LiteralPath $obsolete)) 'Obsolete versions would cause duplicate mod IDs.'
    & "$fixture/build_and_copy.ps1" -ModsDirectory $mods -JavaHome $java
    Assert ((Get-FileHash -LiteralPath $installed).Hash -eq $sourceHash) 'The build wrapper did not install its artifact.'
    Assert (@(Get-ChildItem -LiteralPath $mods -File).Count -eq 1) 'Staging files or temporary backups were left behind.'
    function Copy-Item {
        param([string]$LiteralPath, [string]$Destination, [switch]$Force)
        Set-Content -LiteralPath $Destination -Value 'simulated incomplete copy'
    }
    $rejected = $false
    try { & "$fixture/build_and_copy.ps1" -ModsDirectory $mods -JavaHome $java }
    catch { $rejected = $_.Exception.Message -match 'checksum|hash' }
    Assert $rejected 'A corrupt staged copy was promoted.'
    Assert ((Get-FileHash -LiteralPath $installed).Hash -eq $sourceHash) 'A failed copy destroyed the previous artifact.'
    Assert (@(Get-ChildItem -LiteralPath $mods -File).Count -eq 1) 'A rejected staging file was not cleaned up.'
    function Get-CimInstance { [pscustomobject]@{Name = 'javaw.exe'; CommandLine = 'forgeclient --gameDir fake'} }
    $rejected = $false
    try { & $install -JarPath $jar -ModsDirectory $mods -SkipBackup } catch { $rejected = $_.Exception.Message -match 'running' }
    Assert $rejected 'The running-game check was bypassed.'
    Assert ((Get-FileHash -LiteralPath $installed).Hash -eq $sourceHash) 'A rejected install modified the artifact.'
    Write-Output 'INSTALL_BUILD_REGRESSION_PASS: backup, replacement, duplicate removal, wrapper, corrupt-copy rollback and running-game checks'
    $success = $true
} finally {
    if ($success) {
        $resolved = (Resolve-Path -LiteralPath $cases).Path
        $expected = [IO.Path]::GetFullPath((Join-Path $repo '.codex-tmp')) + [IO.Path]::DirectorySeparatorChar
        if (!$resolved.StartsWith($expected, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe test cleanup path.' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
