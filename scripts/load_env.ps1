[CmdletBinding()]
param(
    [string]$EnvFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $PSScriptRoot '..\.env'
}

$resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
$loadedCount = 0

foreach ($rawLine in Get-Content -LiteralPath $resolvedEnvFile -Encoding UTF8) {
    $line = $rawLine.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith('#')) {
        continue
    }

    $parts = $line.Split('=', 2)
    if ($parts.Count -ne 2) {
        throw "Invalid environment entry in ${resolvedEnvFile}: $rawLine"
    }

    $name = $parts[0].Trim()
    $value = $parts[1]
    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid environment variable name '$name' in $resolvedEnvFile"
    }

    Set-Item -LiteralPath "Env:$name" -Value $value
    $loadedCount++
}

Write-Host "Loaded $loadedCount environment variables from $resolvedEnvFile"
