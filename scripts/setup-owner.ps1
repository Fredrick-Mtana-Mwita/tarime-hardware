$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $projectRoot 'backend/target/finishers-backend-0.1.0-SNAPSHOT.jar'
& (Join-Path $PSScriptRoot 'maven.ps1') -pl backend -am package '-DskipTests'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'The backend package was not created.' }
& python (Join-Path $PSScriptRoot 'setup-local-db.py')
if ($LASTEXITCODE -ne 0) { throw 'The project database could not be started.' }
$javaPath = 'D:\Program Files\JetBrains\jdk-24\bin\java.exe'
if ($env:JAVA_HOME) { $javaPath = Join-Path $env:JAVA_HOME 'bin/java.exe' }
Push-Location $projectRoot
try {
  & $javaPath -jar $jarPath --setup-owner
  if ($LASTEXITCODE -ne 0) { throw 'Owner enrollment stopped. Review the message above; no default credentials were installed.' }
} finally { Pop-Location }
