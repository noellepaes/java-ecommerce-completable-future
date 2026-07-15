param(
    [int]$Vus = 50,
    [string]$Duration = "30s",
    [string]$ComposeFile = (Join-Path $PSScriptRoot "..\docker-compose.yml")
)

$ProjectRoot = Split-Path $ComposeFile -Parent
$ResultsDir = Join-Path $ProjectRoot "load-tests\results"
New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null

$tests = @(
    @{ Module = "Auth";        Store = "Postgres"; Endpoint = "POST /api/auth/login";                    File = "scenarios/auth-login.js" },
    @{ Module = "Auth";        Store = "Postgres"; Endpoint = "GET /api/auth/users";                     File = "scenarios/auth-users.js" },
    @{ Module = "Product";     Store = "Postgres"; Endpoint = "GET /api/products";                      File = "scenarios/products-list.js" },
    @{ Module = "Product";     Store = "Postgres"; Endpoint = "GET /api/products/{id}";                File = "scenarios/products-by-id.js" },
    @{ Module = "Customer";    Store = "Postgres"; Endpoint = "GET /api/customers";                    File = "scenarios/customers-list.js" },
    @{ Module = "Customer";    Store = "Postgres"; Endpoint = "GET /api/customers/{id}";               File = "scenarios/customers-by-id.js" },
    @{ Module = "Order";       Store = "Postgres"; Endpoint = "GET /api/orders/customer/{id}";         File = "scenarios/orders-list.js" },
    @{ Module = "Order";       Store = "Postgres"; Endpoint = "POST /api/orders";                       File = "scenarios/orders-create.js" },
    @{ Module = "Order";       Store = "Postgres"; Endpoint = "POST /api/orders/{id}/items";            File = "scenarios/orders-add-item.js" },
    @{ Module = "Order";       Store = "Postgres"; Endpoint = "POST /api/orders/{id}/pay";              File = "scenarios/orders-pay.js" },
    @{ Module = "Payment";     Store = "Postgres"; Endpoint = "POST /api/payments";                    File = "scenarios/payments-process.js" },
    @{ Module = "Redis";       Store = "Redis";    Endpoint = "GET /api/recommendations/customers/{id}"; File = "scenarios/recommendations-read.js" },
    @{ Module = "Redis";       Store = "Redis";    Endpoint = "POST /api/recommendations/.../views";    File = "scenarios/recommendations-views.js" },
    @{ Module = "Checkout";    Store = "Postgres"; Endpoint = "Fluxo: pedido + item + pagamento";      File = "scenarios/checkout-flow.js" }
)

$summary = @()
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportFile = Join-Path $ResultsDir "suite-$timestamp.txt"

Write-Host ""
Write-Host "=== Suite SEQUENCIAL — um endpoint por vez (comparacao no terminal) ===" -ForegroundColor Cyan
Write-Host "Para rodar TODOS em paralelo e ver no Grafana: .\run-all.ps1" -ForegroundColor Yellow
Write-Host "Grafana: http://localhost:3000/d/ecommerce-load-test" -ForegroundColor DarkGray
Write-Host ""

foreach ($test in $tests) {
    $name = [System.IO.Path]::GetFileNameWithoutExtension($test.File)
    Write-Host ">> $($test.Module) — $($test.Endpoint)" -ForegroundColor Yellow

    $output = docker compose -f $ComposeFile --profile load-test run --rm `
        -e VUS=$Vus -e DURATION=$Duration `
        k6 run "/scripts/$($test.File)" 2>&1 | Out-String

  $output | Out-File -FilePath (Join-Path $ResultsDir "$name-$timestamp.log") -Encoding utf8
    Add-Content -Path $reportFile -Value ("`n=== {0} | {1} | {2} ===" -f $test.Module, $test.Store, $test.Endpoint)
    Add-Content -Path $reportFile -Value $output

    $reqs = if ($output -match 'http_reqs\.+?:\s+(\d+)') { $matches[1] } else { "?" }
    $rps = if ($output -match 'http_reqs\.+?:\s+\d+\s+([\d.]+)/s') { $matches[1] } else { "?" }
    $p95 = if ($output -match 'http_req_duration\.+?p\(95\)=([\d.]+ms)') { $matches[1] } else { "?" }
    $failed = if ($output -match 'http_req_failed\.+?:\s+([\d.]+%)') { $matches[1] } else { "?" }
    $checks = if ($output -match 'checks\.+?:\s+([\d.]+%)') { $matches[1] } else { "?" }

    $summary += [PSCustomObject]@{
        Modulo   = $test.Module
        Store    = $test.Store
        Endpoint = $test.Endpoint
        Reqs     = $reqs
        RPS      = $rps
        P95      = $p95
        Falhas   = $failed
        Checks   = $checks
    }

    Start-Sleep -Seconds 3
}

Write-Host ""
Write-Host "=== RESUMO COMPARATIVO ===" -ForegroundColor Green
$summary | Format-Table -AutoSize

$summary | Format-Table -AutoSize | Out-String | Add-Content -Path $reportFile
Write-Host "Relatório salvo em: $reportFile" -ForegroundColor DarkGray
