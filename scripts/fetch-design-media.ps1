# Downloads the Higgsfield-generated exterior pictures and saves them into the project as
# JPEG files, so the design page loads quickly and no longer depends on Higgsfield's servers.
# Run once from the project root in Windows PowerShell:
#   .\scripts\fetch-design-media.ps1
# It only downloads public image files. It needs no account, key or payment.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$target = Join-Path $PSScriptRoot '..\frontend\public\images\design'
New-Item -ItemType Directory -Force -Path $target | Out-Null
$target = (Resolve-Path $target).Path

$base = 'https://d8j0ntlcm91z4.cloudfront.net/user_3JSNAUiBU29RqcNYveF532JGej2/'
$files = [ordered]@{
  'exterior-1.jpg' = 'hf_20261112_112916_8d003157-49b6-43fd-8a9a-f29e7a3251cd.png'
  'exterior-2.jpg' = 'hf_20261112_113054_8f3b5fc6-087e-4454-800e-f897bab2e978.png'
  'exterior-3.jpg' = 'hf_20261112_113054_e0a5b542-b303-497e-a4ba-58824676cb3a.png'
}

$jpeg = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$quality = New-Object System.Drawing.Imaging.EncoderParameters(1)
$quality.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, 84L)

foreach ($name in $files.Keys) {
  $download = Join-Path ([System.IO.Path]::GetTempPath()) $files[$name]
  $path = Join-Path $target $name
  Write-Host "Downloading $name"
  Invoke-WebRequest -Uri ($base + $files[$name]) -OutFile $download
  $image = [System.Drawing.Image]::FromFile($download)
  try { $image.Save($path, $jpeg, $quality) } finally { $image.Dispose() }
  Remove-Item $download
  if ((Get-Item $path).Length -lt 10000) { throw "$name looks incomplete. Delete it and run the script again." }
}
Write-Host "Saved to $target"
Write-Host 'Animation clips are optional: add interior.mp4 and exterior.mp4 to the same folder.'
