[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$WorkspaceRoot = Split-Path -Parent $ProjectRoot
$ExportRoot = Join-Path $WorkspaceRoot 'latex_moth_high_style_export'

if (-not (Test-Path -LiteralPath $ExportRoot)) {
    New-Item -ItemType Directory -Path $ExportRoot | Out-Null
}

Add-Type -AssemblyName System.Drawing

function New-Id {
    return [guid]::NewGuid().ToString()
}

function New-TintedTexture {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$DestinationPath,
        [Parameter(Mandatory = $true)][int[]]$Rgb
    )

    $source = [System.Drawing.Bitmap]::new($SourcePath)
    $destination = [System.Drawing.Bitmap]::new(
        $source.Width,
        $source.Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )

    try {
        for ($x = 0; $x -lt $source.Width; $x++) {
            for ($y = 0; $y -lt $source.Height; $y++) {
                $pixel = $source.GetPixel($x, $y)
                if ($pixel.A -eq 0) {
                    $destination.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                } else {
                    $destination.SetPixel(
                        $x,
                        $y,
                        [System.Drawing.Color]::FromArgb($pixel.A, $Rgb[0], $Rgb[1], $Rgb[2])
                    )
                }
            }
        }
        $destination.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $destination.Dispose()
        $source.Dispose()
    }
}

function Initialize-EyeTextures {
    $sclera = Join-Path $ExportRoot 'tall_sclera.png'
    $irisLeft = Join-Path $ExportRoot 'tall_iris_left.png'
    $irisRight = Join-Path $ExportRoot 'tall_iris_right.png'
    $brows = Join-Path $ExportRoot 'tall_eyebrows.png'
    $lashes = Join-Path $ExportRoot 'tall_eyelashes.png'

    foreach ($required in @($sclera, $irisLeft, $irisRight, $brows, $lashes)) {
        if (-not (Test-Path -LiteralPath $required)) {
            throw "Missing original eye texture: $required"
        }
    }

    New-TintedTexture $sclera    (Join-Path $ExportRoot 'tall_sclera_honey.png')      @(166, 119, 45)
    New-TintedTexture $irisLeft  (Join-Path $ExportRoot 'tall_iris_left_honey.png')  @(255, 231, 166)
    New-TintedTexture $irisRight (Join-Path $ExportRoot 'tall_iris_right_honey.png') @(255, 231, 166)
    New-TintedTexture $brows     (Join-Path $ExportRoot 'tall_eyebrows_honey.png')   @(139, 96, 32)
    New-TintedTexture $lashes    (Join-Path $ExportRoot 'tall_eyelashes_honey.png')  @(139, 96, 32)
}

function Initialize-ModelContext {
    $script:Elements = [System.Collections.ArrayList]::new()
    $script:Groups = @{}
    $script:RootGroups = [System.Collections.ArrayList]::new()
}

function Add-Group {
    param(
        [string]$ParentId,
        [string]$Name,
        [double[]]$Origin = @(0, 0, 0),
        [double[]]$Rotation = @(0, 0, 0)
    )

    $id = New-Id
    $node = [ordered]@{
        name     = $Name
        origin   = $Origin
        rotation = $Rotation
        uuid     = $id
        color    = 0
        isOpen   = $true
        children = [System.Collections.ArrayList]::new()
    }

    $script:Groups[$id] = $node
    if ([string]::IsNullOrWhiteSpace($ParentId)) {
        [void]$script:RootGroups.Add($id)
    } else {
        [void]$script:Groups[$ParentId].children.Add($id)
    }
    return $id
}

function Get-FaceUv {
    param(
        [int[]]$Uv,
        [double]$Width,
        [double]$Height
    )
    return @(
        [double]$Uv[0],
        [double]$Uv[1],
        [double]($Uv[0] + $Width),
        [double]($Uv[1] + $Height)
    )
}

function Add-Cube {
    param(
        [Parameter(Mandatory = $true)][string]$ParentId,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][double[]]$From,
        [Parameter(Mandatory = $true)][double[]]$Size,
        [Parameter(Mandatory = $true)][int[]]$Uv,
        [double]$Inflate = 0,
        [double[]]$Rotation = @(0, 0, 0),
        [double[]]$Origin = @(0, 0, 0),
        [int]$Texture = 0
    )

    $to = @(
        [double]($From[0] + $Size[0]),
        [double]($From[1] + $Size[1]),
        [double]($From[2] + $Size[2])
    )

    $faces = [ordered]@{
        north = [ordered]@{ uv = (Get-FaceUv $Uv $Size[0] $Size[1]); texture = $Texture }
        south = [ordered]@{ uv = (Get-FaceUv $Uv $Size[0] $Size[1]); texture = $Texture }
        east  = [ordered]@{ uv = (Get-FaceUv $Uv $Size[2] $Size[1]); texture = $Texture }
        west  = [ordered]@{ uv = (Get-FaceUv $Uv $Size[2] $Size[1]); texture = $Texture }
        up    = [ordered]@{ uv = (Get-FaceUv $Uv $Size[0] $Size[2]); texture = $Texture }
        down  = [ordered]@{ uv = (Get-FaceUv $Uv $Size[0] $Size[2]); texture = $Texture }
    }

    $id = New-Id
    $element = [ordered]@{
        name     = $Name
        from     = $From
        to       = $to
        rotation = $Rotation
        origin   = $Origin
        inflate  = $Inflate
        color    = 0
        type     = 'cube'
        autouv   = 0
        uuid     = $id
        faces    = $faces
    }

    [void]$script:Elements.Add($element)
    [void]$script:Groups[$ParentId].children.Add($id)
    return $id
}

function Add-Part {
    param(
        [string]$ParentId,
        [string]$Name,
        [double[]]$Origin,
        [double[]]$Rotation,
        [object[]]$Boxes
    )

    $partId = Add-Group $ParentId $Name $Origin $Rotation
    foreach ($box in $Boxes) {
        Add-Cube -ParentId $partId -Name $box.Name -From $box.From -Size $box.Size -Uv $box.Uv -Inflate $box.Inflate -Rotation @(0, 0, 0) -Origin @(0, 0, 0) -Texture 0 | Out-Null
    }
    return $partId
}

function Add-TallEyes {
    param([Parameter(Mandatory = $true)][string]$HeadId)

    $eyesId = Add-Group $HeadId 'TallEyeStyle' @(0, 0, 0) @(0, 0, 0)
    $headFrom = @(-4, -8, -4)
    $headSize = @(8, 8, 8)
    Add-Cube -ParentId $eyesId -Name 'TallSclera' -From $headFrom -Size $headSize -Uv @(0, 0) -Inflate 0.012 -Texture 1 | Out-Null
    Add-Cube -ParentId $eyesId -Name 'TallIrisLeft' -From $headFrom -Size $headSize -Uv @(0, 0) -Inflate 0.018 -Texture 2 | Out-Null
    Add-Cube -ParentId $eyesId -Name 'TallIrisRight' -From $headFrom -Size $headSize -Uv @(0, 0) -Inflate 0.020 -Texture 3 | Out-Null
    Add-Cube -ParentId $eyesId -Name 'TallEyebrows' -From $headFrom -Size $headSize -Uv @(0, 0) -Inflate 0.023 -Texture 4 | Out-Null
    Add-Cube -ParentId $eyesId -Name 'TallEyelashes' -From $headFrom -Size $headSize -Uv @(0, 0) -Inflate 0.026 -Texture 5 | Out-Null
}

function Get-Textures {
    param([string]$BaseTexture)

    $files = @(
        $BaseTexture,
        'tall_sclera_honey.png',
        'tall_iris_left_honey.png',
        'tall_iris_right_honey.png',
        'tall_eyebrows_honey.png',
        'tall_eyelashes_honey.png'
    )

    $result = [System.Collections.ArrayList]::new()
    for ($i = 0; $i -lt $files.Count; $i++) {
        $name = [System.IO.Path]::GetFileNameWithoutExtension($files[$i])
        [void]$result.Add([ordered]@{
            path           = $files[$i]
            name           = $name
            folder         = ''
            id             = (New-Id)
            particle       = ($i -eq 0)
            use_as_default = ($i -eq 0)
        })
    }
    return @($result)
}

function Write-Model {
    param(
        [string]$FileName,
        [string]$ModelName,
        [string]$ModelIdentifier,
        [string]$BaseTexture
    )

    $outliner = foreach ($rootId in $script:RootGroups) {
        $script:Groups[$rootId]
    }

    $model = [ordered]@{
        meta             = [ordered]@{
            format_version = '4.10'
            model_format   = 'modded_entity'
            box_uv         = $true
        }
        name             = $ModelName
        model_identifier = $ModelIdentifier
        model_format     = 'modded_entity'
        box_uv            = $true
        resolution       = [ordered]@{ width = 96; height = 96 }
        texture_width    = 96
        texture_height   = 96
        visible_box      = @(2, 2, 2)
        elements         = @($script:Elements)
        outliner         = @($outliner)
        textures         = (Get-Textures $BaseTexture)
        animations       = @()
    }

    $json = $model | ConvertTo-Json -Depth 60
    $path = Join-Path $ExportRoot $FileName
    [System.IO.File]::WriteAllText($path, $json, [System.Text.UTF8Encoding]::new($false))
}

function New-MothModel {
    Initialize-ModelContext

    $rightLeg = Add-Group $null 'RightLeg' @(-2.5, 10.5, 0) @(0, 0, 0)
    Add-Part $rightLeg 'RightThigh_r1' @(0, 0, 0) @(-12.5, 0, 0) @(
        [pscustomobject]@{Name='RightThigh'; From=@(-2, 0, -2); Size=@(4, 7, 4); Uv=@(16, 46); Inflate=0}
    ) | Out-Null
    $rightLower = Add-Group $rightLeg 'RightLowerLeg' @(0, 6.375, -3.45) @(0, 0, 0)
    Add-Part $rightLower 'RightCalf_r1' @(0, 0, 0) @(50, 0, 0) @(
        [pscustomobject]@{Name='RightCalf'; From=@(-1.99, -0.125, -2.9); Size=@(4, 6, 4); Uv=@(52, 16); Inflate=0}
    ) | Out-Null
    $rightFoot = Add-Group $rightLower 'RightFoot' @(0, 0.8, 7.175) @(0, 0, 0)
    Add-Part $rightFoot 'RightArch_r1' @(0, 0, 0) @(-20, 0, 0) @(
        [pscustomobject]@{Name='RightArch'; From=@(-2, -8.45, -0.725); Size=@(4, 6, 3); Uv=@(56, 26); Inflate=0.005}
    ) | Out-Null
    Add-Cube -ParentId $rightFoot -Name 'RightPad' -From @(-2, 0, -1.5) -Size @(4, 2, 4) -Uv @(56, 9) -Origin @(0, 4.325, -4.425) | Out-Null

    $leftLeg = Add-Group $null 'LeftLeg' @(2.5, 10.5, 0) @(0, 0, 0)
    Add-Part $leftLeg 'LeftThigh_r1' @(0, 0, 0) @(-12.5, 0, 0) @(
        [pscustomobject]@{Name='LeftThigh'; From=@(-2, 0, -2); Size=@(4, 7, 4); Uv=@(48, 46); Inflate=0}
    ) | Out-Null
    $leftLower = Add-Group $leftLeg 'LeftLowerLeg' @(0, 6.375, -3.45) @(0, 0, 0)
    Add-Part $leftLower 'LeftCalf_r1' @(0, 0, 0) @(50, 0, 0) @(
        [pscustomobject]@{Name='LeftCalf'; From=@(-2.01, -0.125, -2.9); Size=@(4, 6, 4); Uv=@(32, 54); Inflate=0}
    ) | Out-Null
    $leftFoot = Add-Group $leftLower 'LeftFoot' @(0, 0.8, 7.175) @(0, 0, 0)
    Add-Part $leftFoot 'LeftArch_r1' @(0, 0, 0) @(-20, 0, 0) @(
        [pscustomobject]@{Name='LeftArch'; From=@(-2, -8.45, -0.725); Size=@(4, 6, 3); Uv=@(16, 57); Inflate=0.005}
    ) | Out-Null
    Add-Cube -ParentId $leftFoot -Name 'LeftPad' -From @(-2, 0, -1.5) -Size @(4, 2, 4) -Uv @(48, 57) -Origin @(0, 4.325, -4.425) | Out-Null

    $head = Add-Group $null 'Head' @(0, -0.5, 0) @(0, 0, 0)
    Add-Cube -ParentId $head -Name 'HeadBase' -From @(-4, -8, -4) -Size @(8, 8, 8) -Uv @(0, 0) | Out-Null
    Add-Cube -ParentId $head -Name 'Muzzle' -From @(-2, -3, -5.5) -Size @(4, 2, 2) -Uv @(25, 16) | Out-Null
    Add-Cube -ParentId $head -Name 'MuzzleTip' -From @(-1.5, -1, -4.75) -Size @(3, 1, 1) -Uv @(47, 16) | Out-Null
    $rightAntenna = Add-Group $head 'RightAntenna' @(-3.52, -9, -1.76) @(20.25, 32.5, 75)
    Add-Cube -ParentId $rightAntenna -Name 'RightAntennaBase' -From @(-3.23, -1.9, 0.06) -Size @(6, 1, 1) -Uv @(56, 35) | Out-Null
    Add-Cube -ParentId $rightAntenna -Name 'RightAntennaTip' -From @(-2.23, -0.9, 0.06) -Size @(4, 1, 1) -Uv @(64, 48) | Out-Null
    $leftAntenna = Add-Group $head 'LeftAntenna' @(3.52, -9, -1.76) @(20.25, -32.5, -75)
    Add-Cube -ParentId $leftAntenna -Name 'LeftAntennaBase' -From @(-2.77, -1.9, 0.06) -Size @(6, 1, 1) -Uv @(64, 46) | Out-Null
    Add-Cube -ParentId $leftAntenna -Name 'LeftAntennaTip' -From @(-1.77, -0.9, 0.06) -Size @(4, 1, 1) -Uv @(64, 50) | Out-Null
    $hair = Add-Group $head 'Hair' @(0, 0, 0) @(0, 0, 0)
    Add-Cube -ParentId $hair -Name 'HairOuter' -From @(-4, -8, -4) -Size @(8, 8, 8) -Uv @(0, 16) -Inflate 0.2 | Out-Null
    Add-Cube -ParentId $hair -Name 'HairCrest' -From @(-4, -8, -4) -Size @(8, 6, 8) -Uv @(0, 32) -Inflate 0.3 | Out-Null
    Add-TallEyes $head

    $torso = Add-Group $null 'Torso' @(0, -0.5, 0) @(0, 0, 0)
    Add-Cube -ParentId $torso -Name 'TorsoBase' -From @(-4, 0, -2) -Size @(8, 12, 4) -Uv @(32, 0) | Out-Null
    Add-Cube -ParentId $torso -Name 'TorsoFur' -From @(-4, 0.1, -2) -Size @(8, 4, 4) -Uv @(48, 38) -Inflate 0.45 | Out-Null
    Add-Cube -ParentId $torso -Name 'TorsoChest' -From @(-4, 0, -2) -Size @(8, 5, 4) -Uv @(32, 29) -Inflate 0.2 | Out-Null

    $rightWing = Add-Group $torso 'RightWing' @(-0.5, 2.5, 0.8) @(0, 0, 0)
    $rightWingPivot = Add-Group $rightWing 'RightWingPivot' @(-0.4, 0, 0) @(7.5, 12.5, -2.5)
    $wingBase = Add-Group $rightWingPivot 'WingBase_r1' @(0.5, 23.5, -1) @(12.5, -12.5, 15)
    Add-Cube -ParentId $wingBase -Name 'RightWingA' -From @(-10.75, -20.75, 9) -Size @(5, 9, 0) -Uv @(48, 63) | Out-Null
    Add-Cube -ParentId $wingBase -Name 'RightWingB' -From @(-9.75, -11.75, 9) -Size @(4, 1, 0) -Uv @(56, 37) | Out-Null
    Add-Cube -ParentId $wingBase -Name 'RightWingC' -From @(-8.75, -21.75, 8) -Size @(3, 1, 1) -Uv @(64, 52) | Out-Null
    $rightWing2 = Add-Group $torso 'RightWing2' @(-0.5, 4.5, 1) @(0, 0, 0)
    $rightWingPivot2 = Add-Group $rightWing2 'RightWingPivot2' @(0, 0, 0) @(7.5, 0, 0)
    $wingBase2 = Add-Group $rightWingPivot2 'WingBase_r2' @(0.5, 23.5, -1) @(12.5, -12.5, 15)
    Add-Cube -ParentId $wingBase2 -Name 'RightWing2A' -From @(-10.75, -20.75, 9) -Size @(5, 7, 0) -Uv @(58, 63) | Out-Null
    Add-Cube -ParentId $wingBase2 -Name 'RightWing2B' -From @(-8.75, -21.75, 8) -Size @(3, 1, 1) -Uv @(64, 54) | Out-Null
    $wingBase3 = Add-Group $rightWingPivot2 'WingBase_r3' @(1.1, 21.625, -1.425) @(12.5, -12.5, 15)
    Add-Cube -ParentId $wingBase3 -Name 'RightWing2C' -From @(-9.75, -11.75, 9) -Size @(4, 1, 0) -Uv @(64, 15) | Out-Null

    $leftWing = Add-Group $torso 'LeftWing' @(0.9, 2.5, 0.8) @(0, 0, 0)
    $leftWingPivot = Add-Group $leftWing 'LeftWingPivot' @(0, 0, 0) @(7.5, -12.5, 2.5)
    $wingBase4 = Add-Group $leftWingPivot 'WingBase_r4' @(-0.5, 23.5, -1) @(12.5, 12.5, -15)
    Add-Cube -ParentId $wingBase4 -Name 'LeftWingA' -From @(5.75, -21.75, 8) -Size @(3, 1, 1) -Uv @(40, 64) | Out-Null
    Add-Cube -ParentId $wingBase4 -Name 'LeftWingB' -From @(5.75, -20.75, 9) -Size @(5, 9, 0) -Uv @(0, 62) | Out-Null
    Add-Cube -ParentId $wingBase4 -Name 'LeftWingC' -From @(5.75, -11.75, 9) -Size @(4, 1, 0) -Uv @(56, 15) | Out-Null
    $leftWing2 = Add-Group $torso 'LeftWing2' @(0.5, 4.5, 1) @(0, 0, 0)
    $leftWingPivot2 = Add-Group $leftWing2 'LeftWingPivot2' @(0, 0, 0) @(7.5, 0, 0)
    $wingBase5 = Add-Group $leftWingPivot2 'WingBase_r5' @(-0.5, 23.5, -1) @(12.5, 12.5, -15)
    Add-Cube -ParentId $wingBase5 -Name 'LeftWing2A' -From @(5.75, -21.75, 8) -Size @(3, 1, 1) -Uv @(64, 56) | Out-Null
    Add-Cube -ParentId $wingBase5 -Name 'LeftWing2B' -From @(5.75, -20.75, 9) -Size @(5, 7, 0) -Uv @(30, 64) | Out-Null
    $wingBase6 = Add-Group $leftWingPivot2 'WingBase_r6' @(-1.1, 21.625, -1.425) @(12.5, 12.5, -15)
    Add-Cube -ParentId $wingBase6 -Name 'LeftWing2C' -From @(5.75, -11.75, 9) -Size @(4, 1, 0) -Uv @(64, 37) | Out-Null

    $tail = Add-Group $torso 'Tail' @(0, 10.4, -0.4) @(0, 0, 0)
    $tailPrimary = Add-Group $tail 'TailPrimary' @(0, -0.4, 0.3) @(7.5, 0, 0)
    $tailBase1 = Add-Group $tailPrimary 'Base_r1' @(0, 0.4, -0.3) @(62.5, 0, 0)
    Add-Cube -ParentId $tailBase1 -Name 'TailBase1' -From @(-2, 0.75, -1.5) -Size @(4, 4, 4) -Uv @(56, 0) | Out-Null
    $tailSecondary = Add-Group $tailPrimary 'TailSecondary' @(0, 1.9, 3.2) @(0, 0, 0)
    $tailBase2 = Add-Group $tailSecondary 'Base_r2' @(0, 0.65, 0.8) @(80, 0, 0)
    Add-Cube -ParentId $tailBase2 -Name 'TailBase2' -From @(-2.5, -0.45, -2) -Size @(5, 8, 5) -Uv @(32, 16) -Inflate 0.2 | Out-Null

    $rightArm = Add-Group $null 'RightArm' @(-5, 1.5, 0) @(0, 0, 0)
    Add-Cube -ParentId $rightArm -Name 'RightArmMesh' -From @(-3, -2, -2) -Size @(4, 12, 4) -Uv @(0, 46) | Out-Null
    $leftArm = Add-Group $null 'LeftArm' @(5, 1.5, 0) @(0, 0, 0)
    Add-Cube -ParentId $leftArm -Name 'LeftArmMesh' -From @(-1, -2, -2) -Size @(4, 12, 4) -Uv @(32, 38) | Out-Null

    Write-Model 'latex_moth_tall_honey_eyes.bbmodel' 'Latex Moth - Tall Honey Eyes' 'latex_moth_tall_honey_eyes' 'latex_moth.png'
}

function New-DeerModel {
    Initialize-ModelContext

    $rightLeg = Add-Group $null 'RightLeg' @(-2.5, 10.5, 0) @(0, 0, 0)
    Add-Part $rightLeg 'RightThigh_r1' @(0, 0, 0) @(-12.5, 0, 0) @(
        [pscustomobject]@{Name='RightThigh'; From=@(-2, 0, -2); Size=@(4, 7, 4); Uv=@(32, 44); Inflate=0}
    ) | Out-Null
    $rightLower = Add-Group $rightLeg 'RightLowerLeg' @(0, 6.375, -3.45) @(0, 0, 0)
    Add-Part $rightLower 'RightCalf_r1' @(0, 0, 0) @(50, 0, 0) @(
        [pscustomobject]@{Name='RightCalf'; From=@(-1.99, -0.125, -2.9); Size=@(4, 6, 4); Uv=@(48, 22); Inflate=0}
    ) | Out-Null
    $rightFoot = Add-Group $rightLower 'RightFoot' @(0, 0.8, 7.175) @(0, 0, 0)
    Add-Part $rightFoot 'RightArch_r1' @(0, 0, 0) @(-20, 0, 0) @(
        [pscustomobject]@{Name='RightArch'; From=@(-2, -8.45, -0.725); Size=@(4, 6, 3); Uv=@(48, 46); Inflate=0.005}
    ) | Out-Null
    Add-Cube -ParentId $rightFoot -Name 'RightPad' -From @(-2, 0, -2.5) -Size @(4, 2, 5) -Uv @(47, 39) -Origin @(0, 4.325, -4.425) | Out-Null

    $leftLeg = Add-Group $null 'LeftLeg' @(2.5, 10.5, 0) @(0, 0, 0)
    Add-Part $leftLeg 'LeftThigh_r1' @(0, 0, 0) @(-12.5, 0, 0) @(
        [pscustomobject]@{Name='LeftThigh'; From=@(-2, 0, -2); Size=@(4, 7, 4); Uv=@(0, 48); Inflate=0}
    ) | Out-Null
    $leftLower = Add-Group $leftLeg 'LeftLowerLeg' @(0, 6.375, -3.45) @(0, 0, 0)
    Add-Part $leftLower 'LeftCalf_r1' @(0, 0, 0) @(50, 0, 0) @(
        [pscustomobject]@{Name='LeftCalf'; From=@(-2.01, -0.125, -2.9); Size=@(4, 6, 4); Uv=@(48, 0); Inflate=0}
    ) | Out-Null
    $leftFoot = Add-Group $leftLower 'LeftFoot' @(0, 0.8, 7.175) @(0, 0, 0)
    Add-Part $leftFoot 'LeftArch_r1' @(0, 0, 0) @(-20, 0, 0) @(
        [pscustomobject]@{Name='LeftArch'; From=@(-2, -8.45, -0.725); Size=@(4, 6, 3); Uv=@(29, 55); Inflate=0.005}
    ) | Out-Null
    Add-Cube -ParentId $leftFoot -Name 'LeftPad' -From @(-2, 0, -2.5) -Size @(4, 2, 5) -Uv @(24, 0) -Origin @(0, 4.325, -4.425) | Out-Null

    $head = Add-Group $null 'Head' @(0, -0.5, 0) @(0, 0, 0)
    Add-Cube -ParentId $head -Name 'HeadBase' -From @(-4, -8, -4) -Size @(8, 8, 8) -Uv @(0, 16) | Out-Null
    Add-Cube -ParentId $head -Name 'Muzzle' -From @(-2, -3, -6) -Size @(4, 2, 2) -Uv @(52, 32) | Out-Null
    Add-Cube -ParentId $head -Name 'MuzzleTip' -From @(-1.5, -1, -5) -Size @(3, 1, 1) -Uv @(0, 16) | Out-Null
    $nose = Add-Group $head 'Nose_r1' @(0.5, 26, 0) @(10, 0, 0)
    Add-Cube -ParentId $nose -Name 'Nose' -From @(-1, -29.625, -0.95) -Size @(1, 1, 1) -Uv @(41, 6) | Out-Null
    $hair = Add-Group $head 'Hair' @(0, 0, 0) @(0, 0, 0)
    Add-Cube -ParentId $hair -Name 'HairOuter' -From @(-4, -8, -4) -Size @(8, 8, 8) -Uv @(0, 0) -Inflate 0.2 | Out-Null
    Add-Cube -ParentId $hair -Name 'HairCrest' -From @(-4, -8, -4) -Size @(8, 6, 8) -Uv @(24, 8) -Inflate 0.3 | Out-Null
    $rightEar = Add-Group $head 'RightEar' @(-3, -6, -0.5) @(0, 0, 0)
    $rightEarPivot = Add-Group $rightEar 'RightEarPivot' @(0, 0, 0) @(0, 0, -35)
    $earR = Add-Group $rightEarPivot 'cube_r1' @(19.25, 22.65, -0.5) @(0, 0, -30)
    Add-Cube -ParentId $earR -Name 'RightEarA' -From @(-8.9, -30.6, -0.75) -Size @(4, 1, 3) -Uv @(37, 0) | Out-Null
    Add-Cube -ParentId $earR -Name 'RightEarB' -From @(-9.95, -30.1, -1.25) -Size @(5, 1, 4) -Uv @(0, 59) | Out-Null
    $leftEar = Add-Group $head 'LeftEar' @(3, -6, -0.5) @(0, 0, 0)
    $leftEarPivot = Add-Group $leftEar 'LeftEarPivot' @(0, 0, 0) @(0, 0, 35)
    $earL = Add-Group $leftEarPivot 'cube_r2' @(-19.25, 22.65, -0.5) @(0, 0, 30)
    Add-Cube -ParentId $earL -Name 'LeftEarA' -From @(4.9, -30.6, -0.75) -Size @(4, 1, 3) -Uv @(32, 22) | Out-Null
    Add-Cube -ParentId $earL -Name 'LeftEarB' -From @(4.95, -30.1, -1.25) -Size @(5, 1, 4) -Uv @(43, 55) | Out-Null

    $rightAntler = Add-Group $head 'RightAntler' @(0, 24.5, 0) @(0, 0, 0)
    $antlerR1 = Add-Group $rightAntler 'Antler_r1' @(0, 0.5, 0) @(15, -5, -7.5)
    Add-Cube -ParentId $antlerR1 -Name 'RightAntlerA' -From @(1, -35, 6.75) -Size @(1, 3, 1) -Uv @(0, 32) | Out-Null
    Add-Cube -ParentId $antlerR1 -Name 'RightAntlerB' -From @(1, -37.25, 7.75) -Size @(1, 6, 1) -Uv @(0, 0) | Out-Null
    $antlerR2 = Add-Group $rightAntler 'Antler_r2' @(0, 0, 0) @(25, -5, -7.5)
    Add-Cube -ParentId $antlerR2 -Name 'RightAntlerC' -From @(1, -34.5, 12) -Size @(1, 4, 1) -Uv @(0, 18) | Out-Null
    $antlerR3 = Add-Group $rightAntler 'Antler_r3' @(0, 0, 0) @(-10, -5, -7.5)
    Add-Cube -ParentId $antlerR3 -Name 'RightAntlerD' -From @(1, -38, -7.25) -Size @(1, 4, 1) -Uv @(4, 18) | Out-Null
    $leftAntler = Add-Group $head 'LeftAntler' @(0, 24.5, 0) @(0, 0, 0)
    $antlerL1 = Add-Group $leftAntler 'Antler_r4' @(0, 0, 0) @(25, 5, 7.5)
    Add-Cube -ParentId $antlerL1 -Name 'LeftAntlerA' -From @(-2, -34.5, 12) -Size @(1, 4, 1) -Uv @(24, 0) | Out-Null
    $antlerL2 = Add-Group $leftAntler 'Antler_r5' @(0, 0, 0) @(-10, 5, 7.5)
    Add-Cube -ParentId $antlerL2 -Name 'LeftAntlerB' -From @(-2, -38, -7.25) -Size @(1, 4, 1) -Uv @(16, 39) | Out-Null
    $antlerL3 = Add-Group $leftAntler 'Antler_r6' @(0, 0, 0) @(15, 5, 7.5)
    Add-Cube -ParentId $antlerL3 -Name 'LeftAntlerC' -From @(-2, -37.25, 7.75) -Size @(1, 6, 1) -Uv @(4, 0) | Out-Null
    Add-Cube -ParentId $antlerL3 -Name 'LeftAntlerD' -From @(-2, -35, 6.75) -Size @(1, 3, 1) -Uv @(12, 32) | Out-Null
    Add-TallEyes $head

    $torso = Add-Group $null 'Torso' @(0, -0.5, 0) @(0, 0, 0)
    Add-Cube -ParentId $torso -Name 'TorsoBase' -From @(-4, 0, -2) -Size @(8, 12, 4) -Uv @(28, 28) | Out-Null
    $tail = Add-Group $torso 'Tail' @(0, 10.5, 0) @(0, 0, 0)
    $tailPrimary = Add-Group $tail 'TailPrimary' @(0, 0, 0) @(0, 0, 0)
    $tailBase1 = Add-Group $tailPrimary 'Base_r1' @(0, 0.5, 0) @(67.5, 0, 0)
    Add-Cube -ParentId $tailBase1 -Name 'TailBase1' -From @(-1.5, 1.1, -0.6) -Size @(3, 3, 3) -Uv @(48, 10) | Out-Null
    $tailBase2 = Add-Group $tailPrimary 'Base_r2' @(0, 2.5, 6) @(85, 0, 0)
    Add-Cube -ParentId $tailBase2 -Name 'TailBase2' -From @(-1.5, -2.425, -0.35) -Size @(3, 4, 3) -Uv @(16, 32) -Inflate -0.01 | Out-Null

    $rightArm = Add-Group $null 'RightArm' @(-5, 1.5, 0) @(0, 0, 0)
    Add-Cube -ParentId $rightArm -Name 'RightArmMesh' -From @(-3, -2, -2) -Size @(4, 12, 4) -Uv @(0, 32) | Out-Null
    $leftArm = Add-Group $null 'LeftArm' @(5, 1.5, 0) @(0, 0, 0)
    Add-Cube -ParentId $leftArm -Name 'LeftArmMesh' -From @(-1, -2, -2) -Size @(4, 12, 4) -Uv @(16, 40) | Out-Null

    Write-Model 'latex_deer_tall_honey_eyes.bbmodel' 'Latex Deer - Tall Honey Eyes' 'latex_deer_tall_honey_eyes' 'latex_deer.png'
}

Initialize-EyeTextures
New-MothModel
New-DeerModel

$manifest = @'
Cover-model export

Models:
- latex_moth_tall_honey_eyes.bbmodel
- latex_deer_tall_honey_eyes.bbmodel

Both models use the original Changed 1.20.1 geometry and base textures, with the Tall
(Chinese: 高) eye masks recoloured to a warm cream-gold palette inspired by the supplied
reference image. The original game assets were not modified.

Open the .bbmodel files directly in Blockbench. Keep the PNG files in this same folder
if Blockbench asks to relink textures.
'@
[System.IO.File]::WriteAllText(
    (Join-Path $ExportRoot 'README.txt'),
    $manifest,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "Exported models to: $ExportRoot"
