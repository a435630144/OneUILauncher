# Fetches the SESL8 AARs from public GitHub Packages download links.
#
# GitHub Packages requires a token with `read:packages` even for public packages,
# so Gradle cannot resolve them directly on this machine. The package *web* pages
# however expose short-lived signed S3 download URLs to anonymous visitors.
# This script scrapes those URLs and builds a normal local Maven repository:
#
#   third_party/sesl-maven/sesl/androidx/appcompat/appcompat/1.8.0+.../appcompat-1.8.0+....aar
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools/fetch-sesl.ps1
#
# Then (only if you decide to adopt SESL, see docs/refactor/SESL-DECISION.md):
#   repositories { maven { url = uri("third_party/sesl-maven") } }

$ErrorActionPreference = 'Stop'
$UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
$OutRoot = Join-Path $PSScriptRoot '..\third_party\sesl-maven'

# repo, github package id, groupId, artifactId, version
$Modules = @(
    @('sesl-androidx', 2110021, 'sesl.androidx.appcompat',        'appcompat',        '1.8.0+1.0.21-sesl8+rev2'),
    @('sesl-androidx', 2110024, 'sesl.androidx.core',             'core',             '1.19.0+1.0.7-sesl8+rev1'),
    @('sesl-androidx', 2110025, 'sesl.androidx.core',             'core-ktx',         '1.19.0+1.0.0-sesl8+rev0'),
    @('sesl-androidx', 2110028, 'sesl.androidx.fragment',         'fragment',         '1.9.0+1.0.5-sesl8+rev1'),
    @('sesl-androidx', 2110032, 'sesl.androidx.preference',       'preference',       '1.2.1+1.0.0-sesl8+rev1'),
    @('sesl-androidx', 2110033, 'sesl.androidx.recyclerview',     'recyclerview',     '1.4.0+1.0.12-sesl8+rev3'),
    @('sesl-androidx', 2110041, 'sesl.androidx.viewpager2',       'viewpager2',       '1.1.0+1.0.0-sesl8+rev0'),
    @('sesl-androidx', 2110037, 'sesl.androidx.viewpager',        'viewpager',        '1.1.0+1.0.0-sesl8+rev0'),
    @('sesl-androidx', 2110027, 'sesl.androidx.drawerlayout',     'drawerlayout',     '1.2.0+1.0.0-sesl8+rev0'),
    @('sesl-androidx', 2110023, 'sesl.androidx.coordinatorlayout','coordinatorlayout','1.3.0+1.0.0-sesl8+rev0'),
    @('sesl-androidx', 2110026, 'sesl.androidx.customview',       'customview',       '1.2.0-rc01+1.0.0-sesl8+rev0'),
    @('sesl-androidx', 2110034, 'sesl.androidx.slidingpanelayout','slidingpanelayout','1.2.0+1.0.4-sesl8+rev1'),
    @('sesl-androidx', 2110035, 'sesl.androidx.swiperefreshlayout','swiperefreshlayout','1.2.0-alpha01+1.0.0-sesl8+rev0'),
    @('sesl-androidx', 2110029, 'sesl.androidx.indexscroll',      'indexscroll',      '1.0.0+1.0.0-sesl8+rev0'),
    @('sesl-androidx', 2110030, 'sesl.androidx.picker',           'picker-basic',     '1.0.7+1.0.7-sesl8+rev2'),
    @('sesl-androidx', 2110031, 'sesl.androidx.picker',           'picker-color',     '1.0.2+1.0.3-sesl8+rev0'),
    @('sesl-androidx', 2110022, 'sesl.androidx.apppickerview',    'apppickerview',    '1.0.0+1.0.0-sesl8+rev0')
)

function Get-PageHtml([string]$Url) {
    for ($i = 0; $i -lt 4; $i++) {
        try {
            return (Invoke-WebRequest -UseBasicParsing -Uri $Url -Headers @{ 'User-Agent' = $UA } -TimeoutSec 60).Content
        } catch {
            Start-Sleep -Seconds (2 + $i)
        }
    }
    throw "GET failed: $Url"
}

function Get-SignedUrlMap([string]$Repo, [int]$PackageId, [string]$Version) {
    $url = "https://github.com/tribalfs/$Repo/packages/$PackageId?version=" + [uri]::EscapeDataString($Version)
    $html = Get-PageHtml $url
    $links = [regex]::Matches($html, 'https://github-registry-files\.githubusercontent\.com[^"''<> ]+') |
        ForEach-Object { $_.Value -replace '&amp;', '&' } | Select-Object -Unique
    $map = @{}
    foreach ($link in $links) {
        $m = [regex]::Match($link, 'response-content-disposition=filename%3D([^&]+)')
        if ($m.Success) {
            $name = [uri]::UnescapeDataString($m.Groups[1].Value)
            if (-not $map.ContainsKey($name)) { $map[$name] = $link }
        }
    }
    return $map
}

foreach ($module in $Modules) {
    $repo, $packageId, $groupId, $artifactId, $version = $module
    $dir = Join-Path $OutRoot (($groupId -replace '\.', '\') + "\$artifactId\$version")
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    Write-Host "==> $groupId:$artifactId:$version"
    $map = Get-SignedUrlMap -Repo $repo -PackageId $packageId -Version $version

    foreach ($ext in @('pom', 'module', 'aar')) {
        $name = "$artifactId-$version.$ext"
        if (-not $map.ContainsKey($name)) {
            Write-Warning "  missing $name"
            continue
        }
        $dest = Join-Path $dir $name
        if ((Test-Path -LiteralPath $dest) -and (Get-Item -LiteralPath $dest).Length -gt 0) {
            Write-Host "  skip $name"
            continue
        }
        Invoke-WebRequest -UseBasicParsing -Uri $map[$name] -OutFile $dest -TimeoutSec 300
        Write-Host ("  {0}  {1:N0} bytes" -f $name, (Get-Item -LiteralPath $dest).Length)
    }
}

Write-Host ""
Write-Host "Local Maven repo: $((Resolve-Path $OutRoot).Path)"
