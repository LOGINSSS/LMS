# ============================================================
# 停止全部服务进程（按端口终止 java / vite）
# Usage: powershell -ExecutionPolicy Bypass -File scripts/stop-all.ps1
# ============================================================
$ports = @(8080, 8085, 8086, 8087, 8088, 8090, 8091, 8092, 8093, 8094, 8095, 8096, 8097, 8098, 5173)

foreach ($p in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        $proc = Get-Process -Id $conn[0].OwningProcess -ErrorAction SilentlyContinue
        if ($proc) {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            Write-Host "  [STOP] port $p ($($proc.ProcessName))"
        }
    }
}
Write-Host "Done."
