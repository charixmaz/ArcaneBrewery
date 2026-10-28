param(
    [string]$ServerDir = "$env:TEMP\ArcaneBrewery-Playtest",
    [string]$MinecraftVersion = "26.1.2",
    [int]$MinMemoryMb = 1024,
    [int]$MaxMemoryMb = 2048
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

Write-Host "== ArcaneBrewery local playtest setup =="

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java was not found in PATH. ArcaneBrewery/Paper 26.1 requires Java 25."
}
$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
Write-Host "Java: $javaVersion"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven was not found in PATH."
}

Write-Host "Building ArcaneBrewery..."
Push-Location $repoRoot
try {
    & mvn --batch-mode clean verify
    if ($LASTEXITCODE -ne 0) { throw "ArcaneBrewery build failed." }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $repoRoot "target") -Filter "ArcaneBrewery-*.jar" |
    Where-Object { $_.Name -notlike "original-*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) { throw "Built ArcaneBrewery JAR not found." }

if (Test-Path $ServerDir) {
    $resolvedServerDir = [IO.Path]::GetFullPath($ServerDir)
    if ($resolvedServerDir -eq [IO.Path]::GetPathRoot($resolvedServerDir) -or $resolvedServerDir -eq $repoRoot) {
        throw "Refusing to move the repository or a filesystem root as a test server."
    }
    $backup = "$ServerDir-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Write-Host "Existing test server found; moving it to $backup"
    Move-Item $ServerDir $backup
}
New-Item -ItemType Directory -Force -Path $ServerDir, (Join-Path $ServerDir "plugins") | Out-Null

$headers = @{ "User-Agent" = "ArcaneBrewery-LocalPlaytest/1.0" }
$buildsUrl = "https://fill.papermc.io/v3/projects/paper/versions/$MinecraftVersion/builds"
Write-Host "Resolving Paper $MinecraftVersion..."
$builds = Invoke-RestMethod -Uri $buildsUrl -Headers $headers
$stable = $builds | Where-Object { $_.channel -eq "STABLE" } | Select-Object -First 1
if (-not $stable) { throw "No stable Paper build was found for $MinecraftVersion." }
$paperUrl = $stable.downloads.'server:default'.url
if (-not $paperUrl) { throw "Paper download URL missing." }

Write-Host "Downloading Paper..."
Invoke-WebRequest -Uri $paperUrl -Headers $headers -OutFile (Join-Path $ServerDir "paper.jar")

Copy-Item $jar.FullName (Join-Path $ServerDir "plugins\ArcaneBrewery.jar")
Set-Content -Path (Join-Path $ServerDir "eula.txt") -Value "eula=false" -Encoding ascii
Write-Host "EULA has NOT been accepted. The server owner must review and accept it before startup."

$serverProps = @(
    "online-mode=false",
    "motd=ArcaneBrewery Local Playtest",
    "gamemode=creative",
    "difficulty=peaceful",
    "spawn-protection=0",
    "view-distance=6",
    "simulation-distance=4"
)
Set-Content -Path (Join-Path $ServerDir "server.properties") -Value $serverProps -Encoding ascii

$runBat = @"
@echo off
cd /d "%~dp0"
java -Xms$($MinMemoryMb)M -Xmx$($MaxMemoryMb)M -jar paper.jar --nogui
pause
"@
Set-Content -Path (Join-Path $ServerDir "run.bat") -Value $runBat -Encoding ascii

Write-Host ""
Write-Host "Playtest server prepared:"
Write-Host "  $ServerDir"
Write-Host ""
Write-Host "Start it with:"
Write-Host "  $ServerDir\run.bat"
Write-Host ""
Write-Host "Minecraft multiplayer address:"
Write-Host "  localhost"
Write-Host ""
Write-Host "ArcaneBrewery is standalone; no economy API or provider is required."
