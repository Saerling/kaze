; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;
; Instalador de Kaze para Windows (NSIS 3). Lo llama desktop/scripts/empaquetar_windows.py con:
;   VERSION, FIREFOX, BUILD, ORIGEN (carpeta armada), SALIDA (exe) e ICONO.
;
; - Se instala para el usuario actual, sin pedir permisos de administrador,
;   en %LOCALAPPDATA%\Programs\Kaze. Tus datos quedan aparte, en %APPDATA%\Kaze.
; - Instalar una versión nueva encima actualiza Kaze sin perder nada.
; - "Kaze-…-setup.exe /S /actualizar" es lo que usa Kaze al actualizarse solo:
;   espera a que Kaze se cierre, instala sin preguntar y lo vuelve a abrir.

Unicode true
SetCompressor /SOLID lzma
RequestExecutionLevel user
ManifestDPIAware true

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "LogicLib.nsh"

!define NOMBRE "Fyx"
!define CLAVE_DESINSTALAR "Software\Microsoft\Windows\CurrentVersion\Uninstall\Kaze"
!define ARGUMENTOS '-app "$INSTDIR\browser\application.ini"'

Name "${NOMBRE}"
OutFile "${SALIDA}"
InstallDir "$LOCALAPPDATA\Programs\Kaze"
BrandingText "Fyx ${VERSION} · basado en Zen"
VIProductVersion "0.4.${BUILD}.0"
VIAddVersionKey /LANG=3082 "ProductName" "Fyx"
VIAddVersionKey /LANG=3082 "FileDescription" "Instalador de Fyx"
VIAddVersionKey /LANG=3082 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=3082 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=3082 "LegalCopyright" "MPL 2.0"

!define MUI_ICON "${ICONO}"
!define MUI_UNICON "${ICONO}"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE "Fyx ${VERSION}"
!define MUI_WELCOMEPAGE_TEXT "Fyx es un navegador rápido y bonito basado en Zen y Firefox: espacios, Essentials, vista dividida y más.$\r$\n$\r$\nSi ya tienes Fyx, se actualizará sin perder tus pestañas, favoritos ni contraseñas.$\r$\n$\r$\nNo hace falta ser administrador."
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "Abrir Fyx"
!define MUI_FINISHPAGE_RUN_FUNCTION AbrirKaze

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "Spanish"

Var Actualizar

Function AbrirKaze
  Exec '"$INSTDIR\${EXE}" ${ARGUMENTOS}'
FunctionEnd

; Kaze tiene xul.dll abierto mientras corre: si no se puede abrir para escribir, sigue abierto.
!macro EsperarCierre
  ${If} ${FileExists} "$INSTDIR\xul.dll"
    StrCpy $1 0
    ${Do}
      ClearErrors
      FileOpen $0 "$INSTDIR\xul.dll" a
      ${IfNot} ${Errors}
        FileClose $0
        ${Break}
      ${EndIf}
      ${If} ${Silent}
        IntOp $1 $1 + 1
        ${If} $1 > 120
          Abort
        ${EndIf}
        Sleep 500
      ${Else}
        MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION "Fyx está abierto. Ciérralo para continuar." IDRETRY reintentar
        Abort
        reintentar:
      ${EndIf}
    ${Loop}
  ${EndIf}
!macroend

Function .onInit
  ${GetParameters} $R0
  ClearErrors
  ${GetOptions} $R0 "/actualizar" $R1
  ${IfNot} ${Errors}
    StrCpy $Actualizar 1
  ${EndIf}
FunctionEnd

Section "Kaze" SecKaze
  SectionIn RO
  !insertmacro EsperarCierre

  ; Al actualizar se reemplaza la carpeta entera (los datos del usuario no están aquí).
  ; Solo si de verdad es una instalación de Kaze, para no borrar nada más por error.
  ${If} ${FileExists} "$INSTDIR\kaze\kaze.manifest"
    RMDir /r "$INSTDIR"
  ${EndIf}

  ; Accesos directos con el nombre anterior.
  Delete "$SMPROGRAMS\Kaze.lnk"
  Delete "$DESKTOP\Kaze.lnk"
  SetOutPath "$INSTDIR"
  File /r "${ORIGEN}\*.*"
  WriteUninstaller "$INSTDIR\Desinstalar Fyx.exe"

  CreateShortcut "$SMPROGRAMS\Fyx.lnk" "$INSTDIR\${EXE}" '${ARGUMENTOS}' "$INSTDIR\kaze.ico" 0 SW_SHOWNORMAL "" "Navegador Fyx"
  ${If} $Actualizar != 1
  ${AndIfNot} ${FileExists} "$DESKTOP\Fyx.lnk"
    CreateShortcut "$DESKTOP\Fyx.lnk" "$INSTDIR\${EXE}" '${ARGUMENTOS}' "$INSTDIR\kaze.ico" 0 SW_SHOWNORMAL "" "Navegador Fyx"
  ${EndIf}

  WriteRegStr HKCU "${CLAVE_DESINSTALAR}" "DisplayName" "Fyx"
  WriteRegStr HKCU "${CLAVE_DESINSTALAR}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${CLAVE_DESINSTALAR}" "Publisher" "Fyx (basado en Zen)"
  WriteRegStr HKCU "${CLAVE_DESINSTALAR}" "DisplayIcon" "$INSTDIR\kaze.ico"
  WriteRegStr HKCU "${CLAVE_DESINSTALAR}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${CLAVE_DESINSTALAR}" "UninstallString" '"$INSTDIR\Desinstalar Fyx.exe"'
  WriteRegStr HKCU "${CLAVE_DESINSTALAR}" "URLInfoAbout" "https://github.com/Saerling/kaze"
  WriteRegDWORD HKCU "${CLAVE_DESINSTALAR}" "NoModify" 1
  WriteRegDWORD HKCU "${CLAVE_DESINSTALAR}" "NoRepair" 1
  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegDWORD HKCU "${CLAVE_DESINSTALAR}" "EstimatedSize" "$0"

  ; Actualización automática: volver a abrir Kaze.
  ${If} ${Silent}
  ${AndIf} $Actualizar == 1
    Call AbrirKaze
  ${EndIf}
SectionEnd

Section "Uninstall"
  !insertmacro EsperarCierre
  Delete "$SMPROGRAMS\Fyx.lnk"
  Delete "$DESKTOP\Fyx.lnk"
  ${If} ${FileExists} "$INSTDIR\kaze\kaze.manifest"
    RMDir /r "$INSTDIR"
  ${EndIf}
  DeleteRegKey HKCU "${CLAVE_DESINSTALAR}"
  MessageBox MB_OK|MB_ICONINFORMATION "Fyx se desinstaló.$\r$\n$\r$\nTus datos (pestañas, favoritos, contraseñas) siguen en %APPDATA%\Kaze por si vuelves a instalarlo. Puedes borrar esa carpeta si ya no los quieres." /SD IDOK
SectionEnd
