@echo off
timeout /t 3 /nobreak > nul
if exist tpv-actualizacion.jar (
    copy /y tpv-actualizacion.jar app\tpv-tienda-1.0-SNAPSHOT.jar
    del tpv-actualizacion.jar
)
start "" "MiTPV.exe"
del "%~f0"
