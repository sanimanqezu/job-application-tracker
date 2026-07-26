# ============================================================================
#  One-shot Docker setup for the Job Application Tracker.
#  Checks whether Docker is installed. If not, installs Docker Desktop
#  (self-elevating for admin), launches it, and waits until the engine is ready.
#  Nothing else needs manual setup - the backend starts the Postgres container.
#
#  Usage (PowerShell):  ./install-docker.ps1
# ============================================================================

function Test-DockerCli    { [bool](Get-Command docker -ErrorAction SilentlyContinue) }
function Test-DockerEngine { try { docker info *> $null; return ($LASTEXITCODE -eq 0) } catch { return $false } }

# ---- 1. Check ---------------------------------------------------------------
if (Test-DockerCli) {
    Write-Host "[docker] Installed:" (docker --version) -ForegroundColor Green
} else {
    Write-Host "[docker] Not installed." -ForegroundColor Yellow

    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Host "[docker] winget unavailable. Install manually: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
        exit 1
    }

    # ---- 2. Install (self-elevate - installing software requires admin) ------
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
               ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Host "[docker] Requesting administrator rights to install Docker Desktop..." -ForegroundColor Cyan
        Start-Process powershell -Verb RunAs -Wait -ArgumentList "-ExecutionPolicy Bypass -File `"$PSCommandPath`""
        if (Test-DockerEngine) { exit 0 } else { exit $LASTEXITCODE }
    }

    Write-Host "[docker] Installing Docker Desktop via winget..." -ForegroundColor Cyan
    winget install -e --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements
}

# ---- 3. Launch --------------------------------------------------------------
$dd = "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
if ((Test-Path $dd) -and -not (Test-DockerEngine)) {
    Write-Host "[docker] Launching Docker Desktop..." -ForegroundColor Cyan
    Start-Process $dd
}

# ---- 4. Wait until the engine is ready --------------------------------------
Write-Host "[docker] Waiting for the Docker engine to be ready..." -ForegroundColor Cyan
for ($i = 0; $i -lt 150; $i++) {
    if (Test-DockerEngine) { Write-Host "[docker] Engine is ready. You are all set." -ForegroundColor Green; exit 0 }
    Start-Sleep -Seconds 2
}
Write-Host "[docker] Engine not ready yet. If Docker was just installed, a reboot may be required, then run the backend again." -ForegroundColor Yellow
exit 1
