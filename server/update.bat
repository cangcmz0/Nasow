@echo off
rem Paper'i ve tum eklentileri en yeni surume gunceller. Sunucu KAPALIYKEN calistir.
setlocal
cd /d "%~dp0"
java setup\Setup.java --update
pause
