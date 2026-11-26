; Script de Inno Setup para el instalador de MiniMarket Pro.
; Lo usa instalador\construir-instalador.ps1 (y el flujo de GitHub Actions).
; Antes de compilarlo debe existir la imagen de la aplicacion en target\instalador\app\MiniMarket Pro.

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

#define AppName "MiniMarket Pro"
#define AppExe "MiniMarket Pro.exe"
#define AppImage "..\target\instalador\app\MiniMarket Pro"

[Setup]
AppId={{6E2B5C1A-7F3D-4A8E-9B21-4C6D8E0F1A23}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=MiniMarket Pro
AppPublisherURL=https://github.com/paulcayoccori-ship-it/MiniMarket-Pro
AppSupportURL=https://github.com/paulcayoccori-ship-it/MiniMarket-Pro/issues
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
; Se instala sin pedir permisos de administrador (para el usuario actual).
; El usuario puede elegir instalar para todos en el dialogo inicial.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\target\instalador
OutputBaseFilename=MiniMarketPro-Setup-{#AppVersion}
SetupIconFile=minimarket.ico
UninstallDisplayIcon={app}\{#AppExe}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{group}\Desinstalar {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent

; Los datos (base de datos y fotos) quedan en %LOCALAPPDATA%\MiniMarket Pro y NO se borran al desinstalar.
