[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipFrontend,
    [switch]$SkipLayerSync,
    [ValidateRange(30, 300)]
    [int]$StartupTimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $root '.env'
$composeFile = Join-Path $root 'infra\docker-compose.yml'
$logsDirectory = Join-Path $root 'logs'
$runDirectory = Join-Path $root 'run'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.example to .env and configure it first."
}
New-Item -ItemType Directory -Force -Path $logsDirectory, $runDirectory | Out-Null

function Import-LbnEnvironment {
    foreach ($rawLine in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) { continue }
        $parts = $line.Split('=', 2)
        if ($parts.Count -ne 2 -or $parts[0].Trim() -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw "Invalid environment entry in ${envFile}: $rawLine"
        }
        Set-Item -LiteralPath ("Env:" + $parts[0].Trim()) -Value $parts[1]
    }
}

function Resolve-Executable([string[]]$Names) {
    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) { return $command.Source }
    }
    return $null
}

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

function Wait-TcpPort([string]$Name, [int]$Port, [int]$TimeoutSeconds = 45) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-TcpPort $Port) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not listen on port $Port within $TimeoutSeconds seconds."
}

function Get-ListeningProcess([int]$Port) {
    foreach ($line in & "$env:SystemRoot\System32\NETSTAT.EXE" -ano -p tcp) {
        if ($line -match "^\s*TCP\s+\S+:${Port}\s+\S+\s+LISTENING\s+(\d+)\s*$") {
            return Get-Process -Id ([int]$Matches[1]) -ErrorAction SilentlyContinue
        }
    }
    return $null
}

function Save-ManagedProcess([string]$Name, [System.Diagnostics.Process]$Process) {
    $metadata = [ordered]@{
        name = $Name
        pid = $Process.Id
        processName = $Process.ProcessName
        startedAtUtc = $Process.StartTime.ToUniversalTime().ToString('o')
    } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText((Join-Path $runDirectory "$Name.pid.json"), $metadata, $utf8NoBom)
}

function Start-ManagedProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )

    if (Test-TcpPort $Port) {
        $existing = Get-ListeningProcess $Port
        if ($existing) { Save-ManagedProcess $Name $existing }
        Write-Host "[ready] $Name already listens on $Port"
        return
    }

    $stdout = Join-Path $logsDirectory "$Name.out.log"
    $stderr = Join-Path $logsDirectory "$Name.err.log"
    $process = Start-Process -FilePath $Executable -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory -WindowStyle Hidden `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    Save-ManagedProcess $Name $process

    try {
        Wait-TcpPort $Name $Port 60
        Write-Host "[ready] $Name PID=$($process.Id) port=$Port"
    } catch {
        if (Test-Path -LiteralPath $stdout) { Get-Content -Tail 50 -LiteralPath $stdout }
        if (Test-Path -LiteralPath $stderr) { Get-Content -Tail 50 -LiteralPath $stderr }
        throw
    }
}

Import-LbnEnvironment

if ([string]::IsNullOrWhiteSpace($env:LBN_JWT_SECRET) -or
    [Text.Encoding]::UTF8.GetByteCount($env:LBN_JWT_SECRET) -lt 32) {
    throw 'LBN_JWT_SECRET in .env must contain at least 32 UTF-8 bytes.'
}

$docker = Resolve-Executable @('docker.exe', 'docker')
$java = Resolve-Executable @('java.exe', 'java')
$maven = Resolve-Executable @('mvn.cmd', 'mvn.exe', 'mvn')
$node = Resolve-Executable @('node.exe', 'node')
$npm = Resolve-Executable @('npm.cmd', 'npm.exe', 'npm')
if (-not $docker) { throw 'Docker CLI was not found.' }
if (-not $java) { throw 'Java was not found. Install JDK 17 or newer.' }
if (-not $maven -and -not $SkipBuild) { throw 'Maven was not found.' }
if ((-not $SkipFrontend -or -not $SkipLayerSync) -and -not $node) { throw 'Node.js was not found.' }
if ((-not $SkipFrontend -or -not $SkipLayerSync) -and -not $npm) { throw 'npm was not found.' }

# Some IDE terminals expose both Path and PATH. Windows Start-Process treats that
# as a duplicate dictionary key, so retain the canonical Path entry only.
$pathKeys = @([Environment]::GetEnvironmentVariables('Process').Keys | Where-Object { $_ -ieq 'path' })
if ($pathKeys -contains 'Path' -and $pathKeys -contains 'PATH') {
    [Environment]::SetEnvironmentVariable('PATH', $null, 'Process')
}

$dockerOutput = & $docker version --format '{{.Server.Version}}' 2>$null
$dockerReady = $LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace(($dockerOutput -join ''))
if (-not $dockerReady) {
    $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        throw 'Docker Engine is unavailable and Docker Desktop was not found.'
    }
    Write-Host '[wait] Starting Docker Desktop...'
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        Start-Sleep -Seconds 2
        & $docker version --format '{{.Server.Version}}' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop did not become ready in time.' }
}

Write-Host '[start] Docker infrastructure'
& $docker compose --env-file $envFile -f $composeFile up -d
if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed.' }
Wait-TcpPort 'MySQL' ([int]$env:LBN_DB_PORT) 60
Wait-TcpPort 'Redis' ([int]$env:LBN_REDIS_PORT) 60
Wait-TcpPort 'RabbitMQ' ([int]$env:LBN_RABBIT_PORT) 60
Wait-TcpPort 'Nacos' 8848 90

$backendPorts = @(8080, 8081, 8082, 8083, 8084)
$backendAlreadyRunning = @($backendPorts | Where-Object { Test-TcpPort $_ }).Count -gt 0
if (-not $SkipBuild -and -not $backendAlreadyRunning) {
    Write-Host '[build] Maven backend'
    $mavenRepository = Join-Path $root 'backend\.m2'
    New-Item -ItemType Directory -Force -Path $mavenRepository | Out-Null
    Push-Location (Join-Path $root 'backend')
    try {
        & $maven "-Dmaven.repo.local=$mavenRepository" -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Backend Maven build failed.' }
    } finally {
        Pop-Location
    }
} elseif ($backendAlreadyRunning -and -not $SkipBuild) {
    Write-Host '[skip] Backend is already running; skipped build to avoid locked JAR files.'
}

$backendServices = @(
    @{ Name = 'user'; Port = 8081; Jar = 'backend\lbn-user-service\target\lbn-user-service.jar' },
    @{ Name = 'post'; Port = 8082; Jar = 'backend\lbn-post-service\target\lbn-post-service.jar' },
    @{ Name = 'recommend'; Port = 8083; Jar = 'backend\lbn-recommend-service\target\lbn-recommend-service.jar' },
    @{ Name = 'chat'; Port = 8084; Jar = 'backend\lbn-chat-service\target\lbn-chat-service.jar' },
    @{ Name = 'gateway'; Port = 8080; Jar = 'backend\lbn-gateway\target\lbn-gateway.jar' }
)
foreach ($service in $backendServices) {
    $jar = Join-Path $root $service.Jar
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing JAR: $jar. Run without -SkipBuild." }
    Start-ManagedProcess -Name $service.Name -Executable $java -Arguments @('-jar', $jar) `
        -Port $service.Port -WorkingDirectory $root
}

if (-not $SkipLayerSync) {
    $layerDirectory = Join-Path $root 'layer-sync-service'
    if (-not (Test-Path -LiteralPath (Join-Path $layerDirectory 'node_modules'))) {
        Write-Host '[install] layer-sync Node dependencies'
        Push-Location $layerDirectory
        try {
            & $npm install
            if ($LASTEXITCODE -ne 0) { throw 'layer-sync npm install failed.' }
        } finally {
            Pop-Location
        }
    }
    $rabbitUser = [Uri]::EscapeDataString($env:LBN_RABBIT_USER)
    $rabbitPassword = [Uri]::EscapeDataString($env:LBN_RABBIT_PASSWORD)
    $env:RABBIT_URL = "amqp://${rabbitUser}:${rabbitPassword}@$($env:LBN_RABBIT_HOST):$($env:LBN_RABBIT_PORT)"
    $env:NACOS_ADDR = $env:LBN_NACOS_ADDR
    $layerServer = Join-Path $root 'layer-sync-service\server.js'
    Start-ManagedProcess -Name 'layer-sync' -Executable $node -Arguments @($layerServer) `
        -Port 9099 -WorkingDirectory $layerDirectory
}

if (-not $SkipFrontend) {
    $frontendDirectory = Join-Path $root 'frontend'
    if (-not (Test-Path -LiteralPath (Join-Path $frontendDirectory 'node_modules'))) {
        Write-Host '[install] frontend Node dependencies'
        Push-Location $frontendDirectory
        try {
            & $npm install
            if ($LASTEXITCODE -ne 0) { throw 'Frontend npm install failed.' }
        } finally {
            Pop-Location
        }
    }
    $vite = Join-Path $root 'frontend\node_modules\vite\bin\vite.js'
    if (-not (Test-Path -LiteralPath $vite)) {
        throw 'Frontend dependencies are missing. Run npm install in frontend first.'
    }
    Start-ManagedProcess -Name 'frontend' -Executable $node `
        -Arguments @($vite, '--host', '127.0.0.1', '--port', '5173') `
        -Port 5173 -WorkingDirectory $frontendDirectory
}

$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/auth/login' `
    -ContentType 'application/json' -Body '{"username":"mcclellan","password":"lbn123456"}' `
    -TimeoutSec 10
if ($login.code -ne 0) { throw 'Gateway is reachable, but the login verification failed.' }

Write-Host ''
Write-Host 'Little Blue Note is ready:'
Write-Host '  Frontend:      http://127.0.0.1:5173'
Write-Host '  Gateway API:   http://127.0.0.1:8080'
Write-Host '  Nacos:         http://127.0.0.1:8848/nacos'
Write-Host '  RabbitMQ:      http://127.0.0.1:15672'
Write-Host '  Layer sync:    http://127.0.0.1:9099/api/layer/status'
Write-Host '  User login:    mcclellan / lbn123456'
Write-Host '  Admin login:   admin / admin123'
