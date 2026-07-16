[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string]$Repository,

    [ValidateSet('private', 'public')]
    [string]$Visibility = 'private'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root

try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git is required.' }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI is required: https://cli.github.com/' }

    & git rev-parse --is-inside-work-tree | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'The project is not a Git repository.' }
    & git rev-parse --verify HEAD | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Create the first commit before pushing.' }
    & gh auth status | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Run gh auth login before pushing.' }

    & git branch -M main
    & gh repo view $Repository 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        & gh repo create $Repository "--$Visibility"
        if ($LASTEXITCODE -ne 0) { throw 'GitHub repository creation failed.' }
    }

    $remoteUrl = "https://github.com/$Repository.git"
    & git remote get-url origin 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        & git remote set-url origin $remoteUrl
    } else {
        & git remote add origin $remoteUrl
    }

    & git push -u origin main
    if ($LASTEXITCODE -ne 0) { throw 'Git push failed.' }
    Write-Host "Done: https://github.com/$Repository"
} finally {
    Pop-Location
}
