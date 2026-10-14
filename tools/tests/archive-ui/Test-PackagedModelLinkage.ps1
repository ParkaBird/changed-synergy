param(
    [Parameter(Mandatory)][string]$SynergyJar,
    [Parameter(Mandatory)][string]$ChangedJar,
    [Parameter(Mandatory)][string]$MinecraftJar,
    [string]$JavapPath = 'javap'
)

# Inspect production bytecode without loading Minecraft, Forge, or a user world.
$ErrorActionPreference = 'Stop'
$classpath = "$ChangedJar$([IO.Path]::PathSeparator)$MinecraftJar"
$apis = @{}

function Read-ClassApi([string]$Name) {
    if ($apis.ContainsKey($Name)) { return $apis[$Name] }
    $lines = @(& $JavapPath -classpath $classpath -p -s $Name 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect production class $Name" }
    $methods = [Collections.Generic.HashSet[string]]::new()
    $member = $null
    foreach ($line in $lines) {
        if ($line -match '^\s+.+?\s+(?<name>[\w.$<>]+)\([^)]*\).*;\s*$') {
            $member = $Matches.name
            if ($member.Contains('.')) { $member = '<init>' }
        } elseif ($member -and $line -match '^\s+descriptor:\s+(?<descriptor>\(\S+)') {
            [void]$methods.Add("${member}:$($Matches.descriptor)")
            $member = $null
        }
    }
    $header = ($lines | Where-Object { $_ -match '\b(class|interface)\s+' } | Select-Object -First 1)
    while ($header -match '<[^<>]*>') { $header = $header -replace '<[^<>]*>', '' }
    $parents = @()
    if ($header -match '\bextends\s+(.+?)(?:\s+implements\s+|\s*\{|$)') {
        $parents += $Matches[1].Split(',').Trim()
    }
    if ($header -match '\bimplements\s+(.+?)\s*\{') { $parents += $Matches[1].Split(',').Trim() }
    $api = [pscustomobject]@{ Methods = $methods; Parents = $parents }
    $apis[$Name] = $api
    return $api
}

function Resolve-Method([string]$Owner, [string]$Signature, [Collections.Generic.HashSet[string]]$Seen) {
    if (!$Seen.Add($Owner)) { return $false }
    $api = Read-ClassApi $Owner
    if ($api.Methods.Contains($Signature)) { return $true }
    if ($Signature.StartsWith('<init>:')) { return $false }
    foreach ($parent in $api.Parents) {
        if (Resolve-Method $parent $Signature $Seen) { return $true }
    }
    return $false
}

$references = @(& $JavapPath -classpath $SynergyJar -verbose -p `
    net.parkabird.changedsynergy.client.archive.ArchiveOriginModels 2>&1)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect packaged ArchiveOriginModels' }
$checked = 0
$failures = @()
foreach ($line in $references) {
    if ($line -notmatch '^\s+#\d+\s+=\s+(?:Interface)?Methodref\s+.+//\s+(?<owner>[^.\s]+)\.(?<name>[^:]+):(?<descriptor>\S+)') { continue }
    $owner = $Matches.owner.Replace('/', '.')
    $signature = "$($Matches.name.Trim('"')):$($Matches.descriptor)"
    if (!$owner.StartsWith('net.ltxprogrammer.changed.client.renderer.model.') `
        -and !$owner.StartsWith('net.minecraft.client.model.')) { continue }
    $checked++
    $seen = [Collections.Generic.HashSet[string]]::new()
    if (!(Resolve-Method $owner $signature $seen)) { $failures += "$owner.$signature" }
}
if ($checked -lt 10) { throw "Audit did not find enough model references ($checked); refusing a vacuous pass" }
if ($failures.Count) {
    $failures | ForEach-Object { Write-Output "PACKAGED_MODEL_LINKAGE_UNRESOLVED: $_" }
    throw "PACKAGED_MODEL_LINKAGE_FAIL: $($failures.Count) of $checked model references are unresolved"
}
Write-Output "PACKAGED_MODEL_LINKAGE_PASS: $checked model references resolved against production Changed and SRG Minecraft"
