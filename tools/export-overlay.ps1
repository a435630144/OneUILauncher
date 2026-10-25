# 把 One UI 覆盖层套到一份干净的 Lawnchair checkout 上。
#
# 用法：
#   .\tools\export-overlay.ps1 -Target D:\AndroidDevelopment\lawnchair-upstream
#
# 目标目录应当是一份 Lawnchair 15-dev 的干净 clone。脚本只复制
# tools/oneui-overlay.txt 里列出的文件，不会动项目的 legacy/ 和 docs/。

param(
    [Parameter(Mandatory = $true)]
    [string]$Target,

    [string]$Source = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path (Join-Path $Target 'build.gradle'))) {
    throw "目标目录不像 Lawnchair 仓库：$Target"
}

$listFile = Join-Path $PSScriptRoot 'oneui-overlay.txt'
$files = Get-Content $listFile -Encoding UTF8 |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith('#') }

foreach ($relative in $files) {
    $src = Join-Path $Source $relative
    if (-not (Test-Path $src)) {
        throw "源文件不存在：$src"
    }
    $dst = Join-Path $Target $relative
    $dstDir = Split-Path -Parent $dst
    if (-not (Test-Path $dstDir)) {
        New-Item -ItemType Directory -Path $dstDir -Force | Out-Null
    }
    Copy-Item $src $dst -Force
    Write-Host "copied  $relative"
}

Write-Host ""
Write-Host "完成。下一步："
Write-Host "  1. 在 $Target 里构建 :assembleLawnWithQuickstepGithubDebug"
Write-Host "  2. 如有冲突，按 docs/refactor/ARCHITECTURE.md 的说明手动合并"
