@echo off
rem Sky Survival - Windows baslatma dosyasi. Cift tiklaman yeterli.
rem Her acilista eksik Paper/eklentileri indirir, sonra sunucuyu baslatir.
rem Sunucu kapanir ya da coker ise 10 saniye sonra otomatik yeniden acilir.
setlocal
cd /d "%~dp0"
title Sky Survival

rem Sunucuya ayrilacak bellek. Bilgisayarinda 8 GB RAM varsa 4G, 16 GB varsa 6G-8G uygundur.
set RAM=4G
rem Kapaninca otomatik yeniden baslatma: 1 = acik, 0 = kapali
set AUTO_RESTART=1

rem Zip'in icinden calistirilirsa Windows sadece bu dosyayi cikarir; diger dosyalar olmaz.
if not exist "setup\Setup.java" goto :notextracted

call :findjava
if not defined JAVA goto :nojava
"%JAVA%" --version >nul 2>nul || goto :oldjava

:loop
"%JAVA%" setup\Setup.java
if errorlevel 2 goto :setupfailed
if not exist server.jar goto :setupfailed

findstr /b /i /c:"eula=true" eula.txt >nul 2>nul || goto :eula

:run
echo.
echo Sunucu baslatiliyor... Kapatmak icin bu pencereye "stop" yazip Enter'a bas.
echo.
"%JAVA%" -Xms%RAM% -Xmx%RAM% -XX:+IgnoreUnrecognizedVMOptions -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -jar server.jar --nogui
echo.
if not "%AUTO_RESTART%"=="1" goto :end
echo Sunucu kapandi. 10 saniye icinde yeniden baslatilacak.
echo Tamamen kapatmak icin bu pencereyi kapat ya da CTRL+C'ye bas.
timeout /t 10
goto :loop

:end
echo Sunucu kapandi.
pause
exit /b 0

rem Bilgisayarda yuklu Java 25+ JDK'yi bulur (PATH'te eski bir Java olsa bile).
rem Sirayla Oracle, Microsoft ve Adoptium klasorlerine bakar; en son bulunan kullanilir.
:findjava
set "JAVA="
for /d %%D in ("%ProgramFiles%\Java\jdk-2*") do if exist "%%~D\bin\java.exe" set "JAVA=%%~D\bin\java.exe"
for /d %%D in ("%ProgramFiles%\Microsoft\jdk-2*") do if exist "%%~D\bin\java.exe" set "JAVA=%%~D\bin\java.exe"
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-2*") do if exist "%%~D\bin\java.exe" set "JAVA=%%~D\bin\java.exe"
if defined JAVA exit /b 0
where java >nul 2>nul && set "JAVA=java"
exit /b 0

:eula
echo.
echo Minecraft sunucusu acmak icin Minecraft EULA'yi kabul etmen gerekiyor:
echo   https://aka.ms/MinecraftEULA
set "ONAY="
set /p "ONAY=EULA'yi okudum ve kabul ediyorum [e/h]: "
if /i "%ONAY%"=="e" goto :eulaok
echo EULA kabul edilmeden sunucu acilamaz.
pause
exit /b 1

:eulaok
> eula.txt echo eula=true
goto :run

:setupfailed
echo.
echo [HATA] Kurulum tamamlanamadi, sunucu baslatilmadi. Yukaridaki hata mesajina bak.
echo Internet baglantini kontrol edip start.bat'i tekrar calistir.
pause
exit /b 1

:notextracted
echo.
echo [HATA] "setup\Setup.java" dosyasi bulunamadi.
echo Zip dosyasini once bir klasore cikarman gerekiyor:
echo   1. SkySurvival.zip dosyasina sag tikla ve "Tumunu ayikla" / "Extract All" sec.
echo   2. Cikan klasorde SkySurvival\server\start.bat dosyasina cift tikla.
echo.
echo Su anki klasor: %CD%
pause
exit /b 1

:oldjava
echo.
echo [HATA] Bilgisayarindaki Java cok eski. Kullanilan Java:
"%JAVA%" -version
echo.
echo Minecraft 26.1 sunucusu icin Java 25 gerekiyor:
echo   https://adoptium.net/  - JDK 25 indir, kurarken "Add to PATH" secenegini isaretle.
echo Kurduktan sonra bu pencereyi kapatip start.bat'i tekrar ac.
pause
exit /b 1

:nojava
echo.
echo [HATA] Java bulunamadi!
echo Java 25 veya daha yenisini kur: https://adoptium.net/  - kurarken "Add to PATH" secenegini isaretle.
pause
exit /b 1
