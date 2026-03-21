#Requires -Version 5.1
<#
.SYNOPSIS
  Create a GitHub Release and upload OpenKiwi APK (requires: gh auth login)

.EXAMPLE
  .\scripts\release-github.ps1 -Tag v3.2.1 -ApkPath .\OpenKiwi321.apk
#>
param(
    [Parameter(Mandatory = $true)]
    [string] $Tag,

    [Parameter(Mandatory = $true)]
    [string] $ApkPath,

    [string] $Repo = "HuSuuuu/OpenKiwi",

    [string] $Title = "",

    [string] $NotesFile = "docs\RELEASE_NOTES_v3.2.1.md"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error "GitHub CLI (gh) not found. Install from https://cli.github.com/"
}

if (-not (Test-Path -LiteralPath $ApkPath)) {
    Write-Error "APK not found: $ApkPath"
}

$root = Split-Path -Parent $PSScriptRoot
if (-not $Title) {
    $Title = "OpenKiwi $Tag"
}

$notesArg = @()
if (Test-Path -LiteralPath (Join-Path $root $NotesFile)) {
    $notesArg = @("--notes-file", (Join-Path $root $NotesFile))
} elseif (Test-Path -LiteralPath $NotesFile) {
    $notesArg = @("--notes-file", (Resolve-Path $NotesFile))
}

Push-Location $root
try {
    $apkFull = Resolve-Path $ApkPath
    Write-Host "Creating release $Tag on $Repo ..."
    & gh release create $Tag $apkFull @notesArg --repo $Repo --title $Title --latest
    Write-Host "Done. See: https://github.com/$Repo/releases/tag/$Tag"
}
finally {
    Pop-Location
}
