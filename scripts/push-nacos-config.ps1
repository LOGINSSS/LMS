# ============================================================
# Push config files under nacos-config/ to local Nacos (DEFAULT_GROUP)
# Usage: powershell -ExecutionPolicy Bypass -File scripts/push-nacos-config.ps1
# Requires: lms-nacos container up (http://localhost:8848)
# NOTE: keep this file ASCII-only (Windows PowerShell 5.1 parses .ps1 as ANSI)
# ============================================================
param(
    [string]$NacosAddr = "http://localhost:8848"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$configDir = Join-Path $scriptDir "..\nacos-config"

$files = @(
    "lms-common.yaml",
    "lms-auth.yaml",
    "lms-user.yaml",
    "lms-course.yaml",
    "lms-media.yaml",
    "lms-remark.yaml",
    "lms-search.yaml",
    "lms-exam.yaml",
    "lms-learning.yaml",
    "lms-gateway.yaml"
)

foreach ($file in $files) {
    $path = Join-Path $configDir $file
    if (-not (Test-Path $path)) {
        Write-Host "[SKIP] file not found: $path" -ForegroundColor Yellow
        continue
    }
    $content = Get-Content -Path $path -Raw -Encoding UTF8
    $body = @{
        dataId  = $file
        group   = "DEFAULT_GROUP"
        content = $content
        type    = "yaml"
    }
    try {
        $resp = Invoke-RestMethod -Method Post -Uri "$NacosAddr/nacos/v1/cs/configs" -Body $body -TimeoutSec 10
        if ($resp -eq "true") {
            Write-Host "[OK] $file published" -ForegroundColor Green
        } else {
            Write-Host "[WARN] $file response: $resp" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "[FAIL] $file publish error: $_" -ForegroundColor Red
    }
}

Write-Host "`nDone. Verify at http://localhost:8848/nacos (Configuration Management)." -ForegroundColor Cyan
