# ============================================================
# 一键启动全部服务（14 个后端 jar + Vite 前端）
# Usage: powershell -ExecutionPolicy Bypass -File scripts/start-all.ps1
# 说明：用 Start-Process 以独立进程启动（不依赖本会话后台任务），
#       关闭终端不会终止这些进程；停止用 scripts/stop-all.ps1
# NOTE: keep this file ASCII-only (Windows PowerShell 5.1 parses .ps1 as ANSI)
# ============================================================
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

$services = @(
    "lms-user",
    "lms-auth",
    "lms-course",
    "lms-media",
    "lms-remark",
    "lms-search",
    "lms-exam",
    "lms-learning",
    "lms-statistics",
    "lms-ai",
    "lms-kb",
    "lms-grab",
    "lms-calendar",
    "lms-gateway"
)

Write-Host "Starting backend services..."
foreach ($s in $services) {
    $jar = Join-Path $root "$s\target\$s.jar"
    if (Test-Path $jar) {
        Start-Process java -ArgumentList "-jar", $jar -WorkingDirectory $root -WindowStyle Hidden
        Write-Host "  [OK] $s"
    } else {
        Write-Host "  [SKIP] jar not found: $jar" -ForegroundColor Yellow
    }
}

Write-Host "Starting Vite dev server..."
Start-Process -FilePath "node" -ArgumentList "node_modules/vite/bin/vite.js" -WorkingDirectory (Join-Path $root "lms-web")

Write-Host "`nAll started. Wait ~30s, then open http://localhost:5173" -ForegroundColor Cyan
