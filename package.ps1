#requires -version 5.1
# ConfigTool 一键打包: 绿色免解包目录版 + 单文件 exe
# 用法: powershell -ExecutionPolicy Bypass -File package.ps1 [-HttpProxy http://127.0.0.1:10808] [-SkipSmokeTest] [-KeepBuild]
# 说明: JDK(含jpackage/jlink)缺失时自动下载 Temurin 17; Maven 缺失时用工程自带 mvnw; 压缩/打包用 Windows 内置 tar+IExpress, 无需第三方工具
param(
    [string]$HttpProxy = 'http://127.0.0.1:10808',
    [string]$Version = '1.0.5',
    [switch]$SkipSmokeTest,
    [switch]$KeepBuild
)
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
if (-not $Root) { $Root = (Get-Location).Path }
$BuildDir  = Join-Path $Root 'build'
$GreenDir  = Join-Path $Root 'dist\green'
$SingleDir = Join-Path $Root 'dist\single'
$JdkDir    = Join-Path $Root 'tools\jdk'
$ExeName   = 'ConfigTool-portable.exe'
$RuntimeModules = @(
    'java.base','java.compiler','java.datatransfer','java.xml','java.prefs','java.desktop',
    'java.logging','java.management','java.naming','java.security.jgss','java.security.sasl',
    'java.xml.crypto','jdk.unsupported','javafx.base','javafx.graphics','javafx.controls','javafx.fxml'
) -join ','
$AppJavaOptions = @(
    '-Duser.dir=$APPDIR',
    '--add-modules=javafx.controls,javafx.fxml',
    '--add-opens=java.base/java.lang=ALL-UNNAMED',
    '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
    '--add-opens=java.base/java.math=ALL-UNNAMED',
    '--add-opens=java.base/java.util=ALL-UNNAMED',
    '--add-opens=java.base/java.nio=ALL-UNNAMED',
    '--add-opens=java.base/sun.nio.ch=ALL-UNNAMED',
    '--add-opens=java.base/java.io=ALL-UNNAMED'
)
function Write-Step([string]$m) { Write-Host ''; Write-Host ('=== ' + $m) -ForegroundColor Cyan }
function Write-Ok([string]$m) { Write-Host ('  [OK] ' + $m) -ForegroundColor Green }
function Assert-Exit([string]$w) { if ($LASTEXITCODE -ne 0) { throw ($w + ' 失败, exit=' + $LASTEXITCODE) } }
function Remove-File([string]$f) { if (Test-Path -LiteralPath $f) { [System.IO.File]::Delete($f) } }
function Remove-Directory([string]$d) {
    if (-not (Test-Path -LiteralPath $d)) { return }
    Get-ChildItem -LiteralPath $d -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object { try { $_.Attributes = 'Normal' } catch { } }
    [System.IO.Directory]::Delete($d, $true)
}
function Stop-App([string]$n) { Get-Process -Name $n -ErrorAction SilentlyContinue | ForEach-Object { try { $_.Kill() } catch { } } }
function Copy-Contents([string]$from, [string]$to) {
    New-Item -ItemType Directory -Force -Path $to | Out-Null
    Get-ChildItem -LiteralPath $from -Force | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $to -Recurse -Force }
}
function Test-Jdk([string]$jdkHome) {
    if (-not $jdkHome) { return $false }
    return ((Test-Path (Join-Path $jdkHome 'bin\jpackage.exe')) -and (Test-Path (Join-Path $jdkHome 'bin\jlink.exe')) -and (Test-Path (Join-Path $jdkHome 'jmods')))
}
# ---------------- 1. 工具链 ----------------
Write-Step '1/6 准备工具链'
$JavaHome = $null
if (Test-Jdk $env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome) {
    foreach ($r in @('C:\Program Files\Java','C:\Program Files\Eclipse Adoptium','C:\Program Files\Microsoft','C:\Program Files\Amazon Corretto','C:\Program Files\Zulu')) {
        if (-not (Test-Path $r)) { continue }
        Get-ChildItem -LiteralPath $r -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            if (-not $JavaHome -and (Test-Jdk $_.FullName)) { $JavaHome = $_.FullName }
        }
    }
}
if (-not $JavaHome) {
    Write-Host '  未找到含 jpackage/jlink 的 JDK, 自动下载 Temurin 17 便携版 ...'
    New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
    $jdkZip = Join-Path $BuildDir 'temurin17.zip'
    $proxyArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($HttpProxy)) { $proxyArgs = @('-x', $HttpProxy) }
    & curl.exe @('-sSL','--fail','--retry','2') @proxyArgs @('-o',$jdkZip,'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk')
    Assert-Exit '下载 JDK'
    Remove-Directory $JdkDir
    New-Item -ItemType Directory -Force -Path $JdkDir | Out-Null
    & tar.exe -xf $jdkZip -C $JdkDir
    Assert-Exit '解压 JDK'
    $found = Get-ChildItem -LiteralPath $JdkDir -Directory | Where-Object { Test-Jdk $_.FullName } | Select-Object -First 1
    if (-not $found) { throw 'JDK 解压后未找到 jpackage/jlink' }
    $JavaHome = $found.FullName
    Remove-File $jdkZip
    Write-Ok ('已自动安装便携 JDK: ' + $JavaHome)
}
$env:JAVA_HOME = $JavaHome
$env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
$jpackage = Join-Path $JavaHome 'bin\jpackage.exe'
$jlink    = Join-Path $JavaHome 'bin\jlink.exe'
Write-Ok ('JDK: ' + $JavaHome)
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $mvn = Join-Path $Root 'mvnw.cmd'
    if (-not (Test-Path $mvn)) { throw '未找到 mvn, 且工程无 mvnw, 请先安装 Maven' }
}
Write-Ok ('Maven: ' + $mvn)
foreach ($t in @('tar.exe','iexpress.exe')) {
    if (-not (Get-Command $t -ErrorAction SilentlyContinue)) { throw ('缺少 Windows 内置工具: ' + $t) }
}
Write-Ok 'Windows 内置 tar / iexpress 可用(无需第三方压缩打包工具)'

# ---------------- 2. fat jar ----------------
Write-Step '2/6 构建 fat jar (过滤 JavaFX; POI 传递依赖必须保留以满足 JPMS)'
Push-Location $Root
try {
    & $mvn -B -pl configtool-app -am package '-Dexec.skip=true'
    Assert-Exit 'mvn package'
} finally { Pop-Location }
$appJar = Join-Path $Root 'configtool-app\target\configtool-app-1.0.5.jar'
if (-not (Test-Path $appJar)) { throw ('未找到 jar: ' + $appJar) }
Write-Ok ('fat jar: ' + [math]::Round((Get-Item $appJar).Length / 1MB, 1) + ' MB')

# ---------------- 3. jlink 精简运行时 ----------------
Write-Step '3/6 生成精简运行时 jlink'
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
$slimRuntime = Join-Path $BuildDir 'jre-slim'
Remove-Directory $slimRuntime
& $jlink --module-path (Join-Path $JavaHome 'jmods') --add-modules $RuntimeModules --compress=2 --no-header-files --no-man-pages --output $slimRuntime
Assert-Exit 'jlink'
Write-Ok ('精简运行时: ' + [math]::Round(((Get-ChildItem $slimRuntime -Recurse -File | Measure-Object Length -Sum).Sum) / 1MB, 1) + ' MB')

# ---------------- 4. 绿色免解包目录版 ----------------
Write-Step '4/6 生成绿色免解包目录版'
$inputDir = Join-Path $BuildDir 'jpackage-input'
Remove-Directory $inputDir
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item $appJar (Join-Path $inputDir 'ConfigTool.jar') -Force
foreach ($d in @('config','ftl','example')) {
    $srcDir = Join-Path $Root $d
    if (Test-Path $srcDir) { Copy-Item $srcDir (Join-Path $inputDir $d) -Recurse -Force }
}
$appImageDest = Join-Path $BuildDir 'appimage'
Remove-Directory $appImageDest
New-Item -ItemType Directory -Force -Path $appImageDest | Out-Null
$icon = Join-Path $Root 'configtool-app\src\main\resources\com\eouna\configtool\icon\main.ico'
$jpArgs = @(
    '--type','app-image','--name','ConfigTool','--app-version',$Version,
    '--dest',$appImageDest,'--input',$inputDir,
    '--main-jar','ConfigTool.jar','--main-class','com.eouna.configtool.Launcher',
    '--runtime-image',$slimRuntime
)
foreach ($opt in $AppJavaOptions) { $jpArgs += @('--java-options', $opt) }
if (Test-Path $icon) { $jpArgs += @('--icon', $icon) }
& $jpackage @jpArgs
Assert-Exit 'jpackage'
$appImage = Join-Path $appImageDest 'ConfigTool'
# templatelib 必须作为文件存在(热加载依赖), 但放到 --input 之外, 避免被塞进 app.classpath
$templatelib = Join-Path $Root 'templatelib'
if (Test-Path $templatelib) { Copy-Item $templatelib (Join-Path $appImage 'app\templatelib') -Recurse -Force }
Remove-Directory $GreenDir
New-Item -ItemType Directory -Force -Path $GreenDir | Out-Null
Copy-Contents $appImage $GreenDir
Write-Ok ('绿色版: ' + (Join-Path $GreenDir 'ConfigTool.exe'))

# ---------------- 5. 单文件自解压 exe ----------------
Write-Step '5/6 生成单文件版'
$sfxDir = Join-Path $BuildDir 'sfx'
Remove-Directory $sfxDir
New-Item -ItemType Directory -Force -Path $sfxDir | Out-Null
$payloadZip = Join-Path $sfxDir 'ConfigTool.zip'
Push-Location $GreenDir
try {
    & tar.exe '-a' '-cf' $payloadZip '--options' 'zip:compression=store' '.'
    if ($LASTEXITCODE -ne 0) {
        Remove-File $payloadZip
        & tar.exe '-a' '-cf' $payloadZip '.'
        Assert-Exit 'tar 打包 zip'
    }
} finally { Pop-Location }
Write-Ok ('payload zip: ' + [math]::Round((Get-Item $payloadZip).Length / 1MB, 1) + ' MB')
# 首次运行解压到 %LOCALAPPDATA%\ConfigTool, 之后直接启动; 用 Windows 内置 tar 解压(比 Expand-Archive 快很多)
$extractLines = @(
    '@echo off',
    'setlocal',
    'set "TARGET=%LOCALAPPDATA%\ConfigTool"',
    'set "APP=%TARGET%\ConfigTool.exe"',
    'if exist "%APP%" goto run',
    'if not exist "%TARGET%" mkdir "%TARGET%"',
    'tar -xf "%~dp0ConfigTool.zip" -C "%TARGET%"',
    ':run',
    'start "" "%APP%"'
)
[System.IO.File]::WriteAllLines((Join-Path $sfxDir 'extract.cmd'), $extractLines, (New-Object System.Text.UTF8Encoding($false)))
New-Item -ItemType Directory -Force -Path $SingleDir | Out-Null
$exePath = Join-Path $SingleDir $ExeName
Remove-File $exePath
# 注意: IExpress 的 [Strings] FILEi 必须写相对路径, 用绝对路径会静默失败(生成空包)
$sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=0
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$exePath
FriendlyName=ConfigTool
AppLaunched=extract.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles
[Strings]
FILE0=ConfigTool.zip
FILE1=extract.cmd
[SourceFiles]
SourceFiles0=$sfxDir\
[SourceFiles0]
%FILE0%=
%FILE1%=
"@
$sedPath = Join-Path $BuildDir 'portable.sed'
[System.IO.File]::WriteAllText($sedPath, $sed, (New-Object System.Text.UTF8Encoding($false)))
$ie = Start-Process -FilePath 'iexpress.exe' -ArgumentList @('/N','/Q',$sedPath) -PassThru -NoNewWindow
if (-not $ie.WaitForExit(1800000)) { try { $ie.Kill() } catch { }; throw 'IExpress 超时' }
$ie.Refresh()
if (-not (Test-Path $exePath)) { throw 'IExpress 打包失败(未生成 exe)' }
Write-Ok ('单文件版: ' + $exePath + '  (' + [math]::Round((Get-Item $exePath).Length / 1MB, 1) + ' MB)')

# ---------------- 6. 冒烟测试 ----------------
if (-not $SkipSmokeTest) {
    Write-Step '6/6 冒烟测试(清空 PATH 中的 java)'
    $savedPath = $env:Path
    $savedJavaHome = $env:JAVA_HOME
    $env:JAVA_HOME = $null
    $env:Path = 'C:\Windows\system32;C:\Windows;C:\Windows\System32\Wbem'
    try {
        $greenExe = Join-Path $GreenDir 'ConfigTool.exe'
        $p1 = Start-Process -FilePath $greenExe -WorkingDirectory $env:TEMP -PassThru
        Start-Sleep -Seconds 20
        if ($p1.HasExited) { throw ('绿色版启动失败, exit=' + $p1.ExitCode) }
        Write-Ok '绿色版启动正常(无需安装 Java)'
        Stop-App 'ConfigTool'
        Remove-Directory (Join-Path $env:LOCALAPPDATA 'ConfigTool')
        $null = Start-Process -FilePath $exePath -PassThru
        $started = $false
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Seconds 5
            if (Get-Process -Name 'ConfigTool' -ErrorAction SilentlyContinue) { $started = $true; break }
        }
        if (-not $started) { throw '单文件版启动失败' }
        Write-Ok '单文件版启动正常(无需安装 Java, 已自动解包)'
    } finally {
        $env:Path = $savedPath
        $env:JAVA_HOME = $savedJavaHome
        Stop-App 'ConfigTool'
    }
}

if (-not $KeepBuild) { Remove-Directory $BuildDir }

Write-Step '打包完成'
Write-Host ('  绿色免解包目录版 : ' + $GreenDir)
Write-Host ('  单文件版         : ' + $exePath)
