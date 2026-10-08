@echo off
rem Sky Survival - Windows baslatma dosyasi. Cift tiklaman yeterli.
rem Ilk acilista Paper'i ve eksik eklentileri indirir, sonra sunucuyu baslatir.
setlocal
cd /d "%~dp0"
title Sky Survival

rem Sunucuya ayrilacak bellek. Bilgisayarinda 8 GB RAM varsa 4G, 16 GB varsa 6G-8G uygundur.
set RAM=4G

where java >nul 2>nul || goto :nojava

java setup\Setup.java
if errorlevel 2 goto :setupfailed

findstr /b /i /c:"eula=true" eula.txt >nul 2>nul || goto :eula

:start
echo.
echo Sunucu baslatiliyor... Kapatmak icin bu pencereye "stop" yazip Enter'a bas.
echo.
java -Xms%RAM% -Xmx%RAM% -XX:+IgnoreUnrecognizedVMOptions -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -jar server.jar --nogui
echo.
echo Sunucu kapandi.
pause
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
goto :start

:setupfailed
echo.
echo Kurulum tamamlanamadi, sunucu baslatilmadi. Yukaridaki hata mesajina bak.
pause
exit /b 1

:nojava
echo Java bulunamadi!
echo Java 25 veya daha yenisini kur: https://adoptium.net/  - kurarken "Add to PATH" secenegini isaretle.
pause
exit /b 1
