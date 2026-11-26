@echo off
REM Abre MiniMarket Pro. No cierres esta ventana mientras uses el sistema.
title MiniMarket Pro
cd /d "%~dp0"
echo Iniciando MiniMarket Pro... (no cierres esta ventana)
echo La primera vez puede tardar varios minutos porque descarga las librerias.
call mvnw.cmd -q javafx:run
if errorlevel 1 (
  echo.
  echo Ocurrio un error al iniciar. Revisa la seccion "Problemas frecuentes" de INSTALACION-WINDOWS.md
  pause
)
