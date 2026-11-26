# Construye el instalador de Windows de MiniMarket Pro.
#
#   powershell -ExecutionPolicy Bypass -File instalador\construir-instalador.ps1
#
# Requisitos: JDK 17+ (con jpackage) e Inno Setup 6 (https://jrsoftware.org/isdl.php).
# Resultado:  target\instalador\MiniMarketPro-Setup-<version>.exe

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

# Version tomada del pom.xml
[xml]$pom = Get-Content (Join-Path $raiz "pom.xml")
$version = $pom.project.version
Write-Host "== MiniMarket Pro $version =="

# 1. Ubicar jpackage
$jpackage = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\jpackage.exe"))) {
    $jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
} else {
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) { $jpackage = $cmd.Source }
}
if (-not $jpackage) {
    $candidato = Get-ChildItem "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Amazon Corretto" -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "bin\jpackage.exe" } | Where-Object { Test-Path $_ } | Select-Object -First 1
    $jpackage = $candidato
}
if (-not $jpackage) { throw "No se encontro jpackage. Instala un JDK 17 o superior y define JAVA_HOME." }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $jpackage) }
Write-Host "jpackage: $jpackage"

# 2. Compilar el .jar
& (Join-Path $raiz "mvnw.cmd") -B -q -DskipTests clean package
if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion con Maven." }
$jar = "minimarket-pro-$version.jar"

# 3. Crear la imagen de la aplicacion (programa + Java incluido)
$salida = Join-Path $raiz "target\instalador"
$entrada = Join-Path $salida "entrada"
New-Item -ItemType Directory -Force $entrada | Out-Null
Copy-Item (Join-Path $raiz "target\$jar") $entrada -Force

& $jpackage --type app-image `
    --name "MiniMarket Pro" `
    --app-version $version `
    --vendor "MiniMarket Pro" `
    --description "Sistema de punto de venta para minimarkets y bodegas" `
    --icon (Join-Path $PSScriptRoot "minimarket.ico") `
    --input $entrada `
    --main-jar $jar `
    --java-options "-Xmx1024m" `
    --java-options "-Dfile.encoding=UTF-8" `
    --dest (Join-Path $salida "app")
if ($LASTEXITCODE -ne 0) { throw "Fallo jpackage." }
Write-Host "Aplicacion lista en: $salida\app\MiniMarket Pro"

# 4. Empaquetar con Inno Setup
$iscc = Get-Command iscc -ErrorAction SilentlyContinue
$isccRuta = if ($iscc) { $iscc.Source } else {
    @("${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe", "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe") |
        Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $isccRuta) {
    Write-Warning "No se encontro Inno Setup 6. Instalalo para generar el .exe instalador. La version portable ya esta en target\instalador\app."
    exit 0
}
& $isccRuta "/DAppVersion=$version" (Join-Path $PSScriptRoot "MiniMarketPro.iss")
if ($LASTEXITCODE -ne 0) { throw "Fallo Inno Setup." }
Write-Host "Instalador listo: $salida\MiniMarketPro-Setup-$version.exe"
