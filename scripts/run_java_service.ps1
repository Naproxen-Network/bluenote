[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath,

    [string]$EnvFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $PSScriptRoot '..\.env'
}

$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$resolvedEnv = (Resolve-Path -LiteralPath $EnvFile).Path

foreach ($rawLine in Get-Content -LiteralPath $resolvedEnv -Encoding UTF8) {
    $line = $rawLine.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith('#')) {
        continue
    }

    $parts = $line.Split('=', 2)
    if ($parts.Count -ne 2) {
        throw "Invalid environment entry in ${resolvedEnv}: $rawLine"
    }

    $name = $parts[0].Trim()
    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid environment variable name '$name' in $resolvedEnv"
    }
    Set-Item -LiteralPath "Env:$name" -Value $parts[1]
}

if ([string]::IsNullOrWhiteSpace($env:LBN_JWT_SECRET) -or
    [Text.Encoding]::UTF8.GetByteCount($env:LBN_JWT_SECRET) -lt 32) {
    throw 'LBN_JWT_SECRET must contain at least 32 UTF-8 bytes.'
}

& java.exe -jar $resolvedJar
exit $LASTEXITCODE
