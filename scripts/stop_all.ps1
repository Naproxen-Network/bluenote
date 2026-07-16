[CmdletBinding()]
param(
    [switch]$StopInfrastructure
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runDirectory = Join-Path $root 'run'

if (Test-Path -LiteralPath $runDirectory) {
    foreach ($pidFile in Get-ChildItem -LiteralPath $runDirectory -Filter '*.pid.json' -File) {
        try {
            $metadata = Get-Content -Raw -Encoding UTF8 -LiteralPath $pidFile.FullName | ConvertFrom-Json
            $process = Get-Process -Id ([int]$metadata.pid) -ErrorAction SilentlyContinue
            if (-not $process) {
                Write-Host "[stopped] $($metadata.name) was already stopped"
            } else {
                $sameName = $process.ProcessName -eq [string]$metadata.processName
                $recordedStart = [DateTime]::Parse([string]$metadata.startedAtUtc).ToUniversalTime()
                $actualStart = $process.StartTime.ToUniversalTime()
                $sameStart = [Math]::Abs(($actualStart - $recordedStart).TotalSeconds) -lt 2
                if (-not $sameName -or -not $sameStart) {
                    Write-Warning "Skipped stale PID file $($pidFile.Name); PID $($metadata.pid) now belongs to another process."
                } else {
                    Stop-Process -Id $process.Id -Force
                    Wait-Process -Id $process.Id -ErrorAction SilentlyContinue
                    Write-Host "[stopped] $($metadata.name) PID=$($process.Id)"
                }
            }
        } finally {
            Remove-Item -LiteralPath $pidFile.FullName -Force -ErrorAction SilentlyContinue
        }
    }
}
if ($StopInfrastructure) {
    $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
    if (-not $docker) { $docker = Get-Command docker -ErrorAction SilentlyContinue }
    if (-not $docker) { throw 'Docker CLI was not found.' }
    & $docker.Source compose --env-file (Join-Path $root '.env') `
        -f (Join-Path $root 'infra\docker-compose.yml') stop
    if ($LASTEXITCODE -ne 0) { throw 'docker compose stop failed.' }
    Write-Host '[stopped] Docker infrastructure (data volumes were preserved)'
} else {
    Write-Host 'Docker infrastructure remains running. Add -StopInfrastructure to stop it.'
}
