# Génère les visuels de la fiche Google Play dans docs/store :
#   icon-512.png      icône haute résolution (512 x 512)
#   feature-1024x500.png  bannière
# Usage : powershell -ExecutionPolicy Bypass -File tools\store-graphics.ps1
Add-Type -AssemblyName System.Drawing

$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root "docs\store"
New-Item -ItemType Directory -Force $out | Out-Null

$bg = [System.Drawing.ColorTranslator]::FromHtml("#3949AB")
$white = [System.Drawing.Color]::White

# Cadenas dessiné dans le même repère que ic_launcher_foreground.xml (108 x 108).
function Draw-Lock($g, [float]$x, [float]$y, [float]$scale) {
    $s = $scale
    $pen = [System.Drawing.Pen]::new($white, [float](6 * $s))
    # Anse : verticales de y=52 à y=44, demi-cercle de rayon 10 centré en (54, 44).
    $shackle = New-Object System.Drawing.Drawing2D.GraphicsPath
    $shackle.AddLine([float]($x + 44 * $s), [float]($y + 52 * $s), [float]($x + 44 * $s), [float]($y + 44 * $s))
    $shackle.AddArc([float]($x + 44 * $s), [float]($y + 34 * $s), [float](20 * $s), [float](20 * $s), 180, 180)
    $shackle.AddLine([float]($x + 64 * $s), [float]($y + 44 * $s), [float]($x + 64 * $s), [float]($y + 52 * $s))
    $g.DrawPath($pen, $shackle)
    $shackle.Dispose(); $pen.Dispose()

    # Corps arrondi avec trou de serrure.
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate
    $r = 8 * $s
    $bx = $x + 38 * $s; $by = $y + 52 * $s; $bw = 32 * $s; $bh = 26 * $s
    $path.AddArc($bx, $by, $r, $r, 180, 90)
    $path.AddArc($bx + $bw - $r, $by, $r, $r, 270, 90)
    $path.AddArc($bx + $bw - $r, $by + $bh - $r, $r, $r, 0, 90)
    $path.AddArc($bx, $by + $bh - $r, $r, $r, 90, 90)
    $path.CloseFigure()
    $path.AddEllipse($x + 50 * $s, $y + 59 * $s, 8 * $s, 8 * $s)
    $path.AddRectangle([System.Drawing.RectangleF]::new([float]($x + 52.5 * $s), [float]($y + 67 * $s), [float](3 * $s), [float](5 * $s)))
    $brush = New-Object System.Drawing.SolidBrush($white)
    $g.FillPath($brush, $path)
    $brush.Dispose(); $path.Dispose()
}

function New-Canvas($w, $h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear($bg)
    return $bmp, $g
}

# Icône 512 : Google Play applique lui-même l'arrondi.
$bmp, $g = New-Canvas 512 512
Draw-Lock $g 0 0 (512 / 108)
$bmp.Save((Join-Path $out "icon-512.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# Bannière 1024 x 500.
$bmp, $g = New-Canvas 1024 500
Draw-Lock $g 20 (250 - 56 * 3.6) 3.6
$titleFont = New-Object System.Drawing.Font("Segoe UI Semibold", 64)
$subFont = New-Object System.Drawing.Font("Segoe UI", 26)
$brush = New-Object System.Drawing.SolidBrush($white)
$g.DrawString("LibreBlock", $titleFont, $brush, 420, 140)
$g.DrawString("Bloque les distractions.", $subFont, $brush, 428, 250)
$g.DrawString("Gratuit, sans pub, sans compte.", $subFont, $brush, 428, 295)
$bmp.Save((Join-Path $out "feature-1024x500.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

Write-Output "Visuels generes dans $out"
