# JMeter result analyzer for LMS load test (ASCII-only, PS 5.1 safe)
# Usage: powershell -ExecutionPolicy Bypass -File scripts/jmeter/analyze.ps1 <path-to.jtl>
# Output: per-sampler success rate / TPS / latency percentiles
param(
    [Parameter(Mandatory = $true)][string]$Jtl
)
$ErrorActionPreference = "Stop"
$sw = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host "Reading $Jtl ..."
$rows = Get-Content $Jtl -TotalCount 2000000 | ConvertFrom-Csv
Write-Host "Total samples: $($rows.Count)"

$ok = ($rows | Where-Object success -eq 'true').Count
$err = $rows.Count - $ok
Write-Host "OK: $ok  FAIL: $err  ErrorRate: $([math]::Round($err * 100.0 / $rows.Count, 3))%"

$ts = $rows | ForEach-Object { [long]$_.timeStamp }
$durationMs = ($ts | Measure-Object -Maximum).Maximum - ($ts | Measure-Object -Minimum).Minimum
$durationS = [math]::Max(1, $durationMs / 1000)
Write-Host "Duration: $([math]::Round($durationS))s  Overall TPS: $([math]::Round($rows.Count / $durationS, 1))/s"
Write-Host ""

Write-Host ("{0,-34} {1,9} {2,9} {3,9} {4,9} {5,8} {6,8} {7,8}" -f "Label","Samples","OK%","TPS","Avg(ms)","P50","P95","P99")
Write-Host ("-" * 100)

foreach ($label in ($rows | Select-Object -ExpandProperty label -Unique)) {
    $g = $rows | Where-Object label -eq $label
    $lat = @($g | ForEach-Object { [double]$_.elapsed } | Sort-Object)
    $n = $lat.Count
    if ($n -eq 0) { continue }
    $idx50 = [math]::Min($n - 1, [int]($n * 0.50))
    $idx95 = [math]::Min($n - 1, [int]($n * 0.95))
    $idx99 = [math]::Min($n - 1, [int]($n * 0.99))
    $okN = ($g | Where-Object success -eq 'true').Count
    $avg = ($lat | Measure-Object -Average).Average
    Write-Host ("{0,-34} {1,9} {2,9} {3,9} {4,9} {5,8} {6,8} {7,8}" -f `
        $label.Substring(0, [math]::Min(34, $label.Length)), $n, `
        [math]::Round($okN * 100.0 / $n, 2), [math]::Round($n / $durationS, 1), `
        [math]::Round($avg, 1), $lat[$idx50], $lat[$idx95], $lat[$idx99])
}
$sw.Stop()
Write-Host ""
Write-Host "Analyze time: $([math]::Round($sw.Elapsed.TotalSeconds, 1))s"
