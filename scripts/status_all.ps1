[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Test-TcpPort([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(700)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

$services = @(
    @{ Name = 'Frontend'; Port = 5173 },
    @{ Name = 'Gateway'; Port = 8080 },
    @{ Name = 'User Service'; Port = 8081 },
    @{ Name = 'Post Service'; Port = 8082 },
    @{ Name = 'Recommend Service'; Port = 8083 },
    @{ Name = 'Chat Service'; Port = 8084 },
    @{ Name = 'Layer Sync'; Port = 9099 },
    @{ Name = 'MySQL'; Port = 3307 },
    @{ Name = 'Redis'; Port = 6379 },
    @{ Name = 'RabbitMQ'; Port = 5672 },
    @{ Name = 'Nacos'; Port = 8848 }
)

$services | ForEach-Object {
    [PSCustomObject]@{
        Service = $_.Name
        Port = $_.Port
        Status = if (Test-TcpPort $_.Port) { 'RUNNING' } else { 'STOPPED' }
    }
} | Format-Table -AutoSize
