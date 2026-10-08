@echo off
rem Paper'i ve tum eklentileri en yeni surume gunceller. Sunucu KAPALIYKEN calistir.
setlocal
cd /d "%~dp0"
if not exist "setup\Setup.java" goto :notextracted

set "JAVA="
for /d %%D in ("%ProgramFiles%\Java\jdk-2*") do if exist "%%~D\bin\java.exe" set "JAVA=%%~D\bin\java.exe"
for /d %%D in ("%ProgramFiles%\Microsoft\jdk-2*") do if exist "%%~D\bin\java.exe" set "JAVA=%%~D\bin\java.exe"
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-2*") do if exist "%%~D\bin\java.exe" set "JAVA=%%~D\bin\java.exe"
if not defined JAVA set "JAVA=java"

"%JAVA%" setup\Setup.java --update
pause
exit /b 0

:notextracted
echo [HATA] "setup\Setup.java" bulunamadi. Zip'i once bir klasore cikar ("Tumunu ayikla"), sonra tekrar dene.
pause
exit /b 1
