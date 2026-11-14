# Downloads the sample 3D model generated with Higgsfield (an unbranded paint tin) so it can be
# uploaded on the staff "3D models" page. Run once from the project root in Windows PowerShell:
#   .\scripts\fetch-model-samples.ps1
# It only downloads public files. It needs no account, key or payment.

$ErrorActionPreference = 'Stop'
$target = Join-Path $PSScriptRoot '..\.local\higgsfield-models'
New-Item -ItemType Directory -Force -Path $target | Out-Null
$target = (Resolve-Path $target).Path

$base = 'https://d8j0ntlcm91z4.cloudfront.net/user_3JSNAUiBU29RqcNYveF532JGej2/'
$files = [ordered]@{
  'paint-tin.glb'        = 'hf_20261112_165000_7e581289-514b-4b9b-945d-4954009379bc.glb'
  'paint-tin-source.png' = 'hf_20261112_164930_bc8b845e-2d4b-4a32-94a6-67b0a1fd6e94.png'
}
foreach ($name in $files.Keys) {
  $path = Join-Path $target $name
  Write-Host "Downloading $name"
  Invoke-WebRequest -Uri ($base + $files[$name]) -OutFile $path
  if ((Get-Item $path).Length -lt 10000) { throw "$name looks incomplete. Delete it and run the script again." }
}
Write-Host "Saved to $target"
Write-Host 'Next: sign in as staff, open "3D models", create a draft for the paint product and upload paint-tin.glb.'
