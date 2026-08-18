@echo off
cd /d "%~dp0"
timeout /t 2 /nobreak > nul

:REINTENTAR
copy /y "tpv-actualizacion.jar" "app\tpv-tienda-1.0-SNAPSHOT.jar" > nul
if errorlevel 1 (
    timeout /t 1 /nobreak > nul
    goto REINTENTAR
)

del "tpv-actualizacion.jar"
start "" "MiTPV.exe"
del "%~f0"
