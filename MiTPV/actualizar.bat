@echo off
setlocal
cd /d "%~dp0"
echo [%date% %time%] Iniciando actualizacion... >> "actualizaciones.log"
ping -n 4 127.0.0.1 > nul

set INTENTOS=0
:REINTENTAR
set /a INTENTOS+=1
copy /y "tpv-actualizacion.jar" "app\tpv-tienda-1.0-SNAPSHOT.jar" > nul 2>&1
if errorlevel 1 (
    if %INTENTOS% GEQ 15 (
        echo [%date% %time%] ERROR: no se pudo copiar el jar tras %INTENTOS% intentos. >> "actualizaciones.log"
        goto FIN
    )
    ping -n 2 127.0.0.1 > nul
    goto REINTENTAR
)

del "tpv-actualizacion.jar" > nul 2>&1
echo [%date% %time%] Actualizacion aplicada correctamente. >> "actualizaciones.log"
start "" "MiTPV.exe"

:FIN
del "%~f0"
