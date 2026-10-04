param([string]$Source = (Join-Path $PSScriptRoot 'navigator_icon.source.png'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$target = Join-Path $repoRoot 'src/main/resources/assets/gtsr/textures/items/lore/future_city_address_witness.png'
$previewAsset = Join-Path $repoRoot 'plan/prosperity/data/assets/items/lore/future_city_address_witness.png'
$evidence = Join-Path $repoRoot 'temp/compass-v74/icon'
[IO.Directory]::CreateDirectory($evidence) | Out-Null
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($previewAsset)) | Out-Null
$inputImage = [Drawing.Bitmap]::FromFile($Source)
try {
    $icon = New-Object Drawing.Bitmap(32, 32, ([Drawing.Imaging.PixelFormat]::Format32bppArgb))
    try {
        # Nearest-neighbour technical conversion, aligning the compass center to destination pixel 16.
        for ($y = 0; $y -lt 32; $y++) {
            for ($x = 0; $x -lt 32; $x++) {
                $sx = [Math]::Min($inputImage.Width - 1, [int][Math]::Floor(($x) * $inputImage.Width / 32))
                $sy = [Math]::Min($inputImage.Height - 1, [int][Math]::Floor(($y) * $inputImage.Height / 32))
                $icon.SetPixel($x, $y, $inputImage.GetPixel($sx, $sy))
            }
        }
        $icon.Save($target, [Drawing.Imaging.ImageFormat]::Png)
        [IO.File]::Copy($target, $previewAsset, $true)
        [IO.File]::Copy($target, (Join-Path $evidence 'native-32.png'), $true)
        $large = New-Object Drawing.Bitmap(320, 320, ([Drawing.Imaging.PixelFormat]::Format32bppArgb))
        try {
            for ($y = 0; $y -lt 320; $y++) {
                for ($x = 0; $x -lt 320; $x++) {
                    $large.SetPixel($x, $y, $icon.GetPixel([int][Math]::Floor($x / 10), [int][Math]::Floor($y / 10)))
                }
            }
            $large.Save((Join-Path $evidence 'nearest-320.png'), [Drawing.Imaging.ImageFormat]::Png)
        } finally { $large.Dispose() }
    } finally { $icon.Dispose() }
} finally { $inputImage.Dispose() }
