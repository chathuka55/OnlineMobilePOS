# Run AFTER reboot (WSL2 must be active for Docker Desktop).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

Write-Host "==> Checking Docker engine..."
$deadline = (Get-Date).AddMinutes(3)
do {
    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    Write-Host "    waiting for Docker Desktop..."
    Start-Sleep -Seconds 5
} while ((Get-Date) -lt $deadline)

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker engine is not ready. Open Docker Desktop and wait until it says Running, then re-run this script."
}

Write-Host "==> Starting Postgres, Redis, MinIO..."
pnpm infra:up

Write-Host "==> Waiting for Postgres health..."
$deadline = (Get-Date).AddMinutes(2)
do {
    $ok = docker inspect --format='{{.State.Health.Status}}' possaas-postgres 2>$null
    if ($ok -eq 'healthy') { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

Write-Host "==> Infra is up. In two terminals run:"
Write-Host "    pnpm api:dev"
Write-Host "    pnpm dev:dashboard"
Write-Host ""
Write-Host "API:       http://localhost:8080"
Write-Host "Swagger:   http://localhost:8080/swagger-ui.html"
Write-Host "Dashboard: http://localhost:3000"
