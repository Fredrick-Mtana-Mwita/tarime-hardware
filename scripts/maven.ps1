param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArguments)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) {
  $knownJdk = 'D:\Program Files\JetBrains\jdk-24'
  if (Test-Path -LiteralPath (Join-Path $knownJdk 'bin/java.exe')) { $env:JAVA_HOME = $knownJdk }
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to your installed JDK 21 or newer.' }
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:PATH
Push-Location $projectRoot
try {
  & (Join-Path $projectRoot 'mvnw.cmd') @MavenArguments
  if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE" }
} finally { Pop-Location }
