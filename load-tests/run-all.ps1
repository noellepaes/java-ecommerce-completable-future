param(
    [int]$VusPerScenario = 10,
    [string]$Duration = "30s",
    [string]$ComposeFile = (Join-Path $PSScriptRoot "..\docker-compose.yml")
)

$totalVus = $VusPerScenario * 14

Write-Host ""
Write-Host "=== Teste PARALELO — todos os endpoints de uma vez ===" -ForegroundColor Cyan
Write-Host "VUs por cenario: $VusPerScenario (~$totalVus VUs no total)" -ForegroundColor DarkGray
Write-Host "Duracao: $Duration" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Abra o Grafana ANTES de rodar:" -ForegroundColor Yellow
Write-Host "  http://localhost:3000/d/ecommerce-load-test" -ForegroundColor White
Write-Host "  Periodo: Last 15 minutes | Refresh: 5s" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Painéis para olhar durante o teste:" -ForegroundColor Yellow
Write-Host "  - Ranking p95 por URI (qual endpoint ficou mais lento)" -ForegroundColor White
Write-Host "  - Latencia p95 por endpoint (grafico)" -ForegroundColor White
Write-Host "  - Requisicoes/s por URI" -ForegroundColor White
Write-Host ""

docker compose -f $ComposeFile --profile load-test run --rm `
    -e VUS_PER_SCENARIO=$VusPerScenario -e DURATION=$Duration `
    k6 run /scripts/scenarios/all-endpoints-parallel.js

Write-Host ""
Write-Host "Pronto. No Grafana, role ate 'Ranking — latencia p95 por URI'." -ForegroundColor Green
Write-Host "Cada linha = um endpoint diferente medido no mesmo teste." -ForegroundColor Green
