#Requires -Version 5.1
<#
.SYNOPSIS
  Create a GitHub Release and upload OpenKiwi APK (requires: gh auth login)

.EXAMPLE
  .\scripts\release-github.ps1 -Tag v3.2.2 -ApkPath .\OpenKiwi322.apk
#>
param(
    [Parameter(Mandatory = $true)]
    [string] $Tag,

    [Parameter(Mandatory = $true)]
    [string] $ApkPath,

    [string] $Repo = "HuSuuuu/OpenKiwi",

    [string] $Title = "",

    [string] $NotesFile = ""
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

if (-not $NotesFile) {
    $ver = $Tag -replace '^[vV]+', ''
    $NotesFile = "docs\RELEASE_NOTES_v$ver.md"
}

$notesArg = @()
$notesJoined = Join-Path $root $NotesFile
if (Test-Path -LiteralPath $notesJoined) {
    $notesArg = @("--notes-file", $notesJoined)
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
