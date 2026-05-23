# Local development helper (Windows PowerShell).
# Postgres/Redis via docker-compose.dev.yml (Postgres on host port 15432).
#
# Usage:
#   .\scripts\local-dev.ps1 infra          # docker postgres + redis only
#   .\scripts\local-dev.ps1 start          # open one window per service (sequential DB migrations)
#   .\scripts\local-dev.ps1 frontend       # npm run dev
#
# Prerequisites: Docker, Java 25 (Gradle toolchain), Node.js 20+

param(
    [Parameter(Position = 0)]
    [ValidateSet("infra", "start", "frontend")]
    [string]$Command = "infra"
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $Root

function Set-LocalDbEnv {
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:15432/notebook_platform"
    $env:DB_HOST = "127.0.0.1"
    $env:DB_PORT = "15432"
    $env:DB_NAME = "notebook_platform"
    $env:DB_USER = "notebook"
    $env:DB_PASSWORD = "notebook"
    $env:DB_RUNTIME_PASSWORD = "notebook"
    $env:DB_MIGRATION_PASSWORD = "notebook"
    $env:JWT_ALLOW_EPHEMERAL_KEYS = "true"
    $env:INTERNAL_SERVICE_JWT_ALLOW_EPHEMERAL_KEYS = "true"
    $env:IDENTITY_SERVICE_URL = "http://localhost:8081"
    $env:WORKSPACE_SERVICE_URL = "http://localhost:8082"
    $env:CONTENT_SERVICE_URL = "http://localhost:8083"
    $env:NOTIFICATION_SERVICE_URL = "http://localhost:8084"
    $env:SEARCH_SERVICE_URL = "http://localhost:8085"
    $env:JWT_JWKS_URI = "http://localhost:8081/.well-known/jwks.json"
    $env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"
}

function Start-Infra {
    docker compose -f docker-compose.dev.yml up -d
    Write-Host "Postgres: localhost:15432 (user/db: notebook / notebook_platform, password: notebook)"
    Write-Host "Redis:    localhost:6379"
}

function Start-ServiceWindow {
    param([string]$GradleTask, [string]$Title)
    $cmd = @"
Set-Location '$Root'
`$env:DB_URL='jdbc:postgresql://127.0.0.1:15432/notebook_platform'
`$env:DB_HOST='127.0.0.1'
`$env:DB_PORT='15432'
`$env:DB_NAME='notebook_platform'
`$env:DB_USER='notebook'
`$env:DB_PASSWORD='notebook'
`$env:DB_RUNTIME_PASSWORD='notebook'
`$env:DB_MIGRATION_PASSWORD='notebook'
`$env:JWT_ALLOW_EPHEMERAL_KEYS='true'
`$env:INTERNAL_SERVICE_JWT_ALLOW_EPHEMERAL_KEYS='true'
`$env:IDENTITY_SERVICE_URL='http://localhost:8081'
`$env:WORKSPACE_SERVICE_URL='http://localhost:8082'
`$env:CONTENT_SERVICE_URL='http://localhost:8083'
`$env:NOTIFICATION_SERVICE_URL='http://localhost:8084'
`$env:SEARCH_SERVICE_URL='http://localhost:8085'
`$env:JWT_JWKS_URI='http://localhost:8081/.well-known/jwks.json'
`$env:CORS_ALLOWED_ORIGINS='http://localhost:5173'
.\gradlew.bat $GradleTask --no-daemon
"@
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd -WindowStyle Normal
    Write-Host "Started: $Title"
}

function Wait-HealthUrl {
    param([string]$Url, [int]$TimeoutSeconds = 180)
    for ($elapsed = 0; $elapsed -lt $TimeoutSeconds; $elapsed += 3) {
        curl.exe -sf $Url 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Start-AllServices {
    Start-Infra
    Start-Sleep -Seconds 4
    # Order matters: Flyway shares public schema; start one JVM at a time.
    $services = @(
        @{ Task = ":identity-service:bootRun"; Title = "identity-service :8081"; Health = "http://localhost:8081/actuator/health" },
        @{ Task = ":workspace-service:bootRun"; Title = "workspace-service :8082"; Health = "http://localhost:8082/api/ok" },
        @{ Task = ":content-service:bootRun"; Title = "content-service :8083"; Health = "http://localhost:8083/actuator/health" },
        @{ Task = ":notification-service:bootRun"; Title = "notification-service :8084"; Health = "http://localhost:8084/actuator/health" },
        @{ Task = ":search-service:bootRun"; Title = "search-service :8085"; Health = "http://localhost:8085/actuator/health" },
        @{ Task = ":api-gateway:bootRun"; Title = "api-gateway :8080"; Health = "http://localhost:8080/actuator/health" }
    )
    foreach ($s in $services) {
        Start-ServiceWindow -GradleTask $s.Task -Title $s.Title
        Write-Host "Waiting for $($s.Title) ..."
        if (-not (Wait-HealthUrl -Url $s.Health)) {
            Write-Error "$($s.Title) did not become healthy within timeout. Check its window for Flyway/Java errors."
            exit 1
        }
        Write-Host "$($s.Title) is healthy."
    }
    Write-Host ""
    Write-Host "Gateway:  http://localhost:8080/actuator/health"
    Write-Host "Frontend: http://localhost:5173 (run: .\scripts\local-dev.ps1 frontend)"
}

function Start-Frontend {
    $fe = Join-Path $Root "frontend"
    if (-not (Test-Path (Join-Path $fe ".env"))) {
        Copy-Item (Join-Path $fe ".env.example") (Join-Path $fe ".env")
    }
    Set-Location $fe
    if (-not (Test-Path "node_modules")) { npm install }
    npm run dev
}

switch ($Command) {
    "infra" { Start-Infra }
    "start" { Start-AllServices }
    "frontend" { Start-Frontend }
}
