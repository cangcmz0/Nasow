#!/usr/bin/env bash
# Sky Survival - Linux/macOS baslatma betigi:  ./start.sh
# Ilk acilista Paper'i ve eksik eklentileri indirir, sonra sunucuyu baslatir.
# Bellek miktarini degistirmek icin:  RAM=6G ./start.sh
set -euo pipefail
cd "$(dirname "$0")"

RAM="${RAM:-4G}"

if ! command -v java >/dev/null 2>&1; then
  echo "Java bulunamadi! Java 25 veya daha yenisini kur: https://adoptium.net/"
  exit 1
fi

status=0
java setup/Setup.java || status=$?
if [ "$status" -ge 2 ]; then
  echo "Kurulum tamamlanamadi, sunucu baslatilmadi. Yukaridaki hata mesajina bak."
  exit 1
fi

if ! grep -qi '^eula=true' eula.txt 2>/dev/null; then
  echo
  echo "Minecraft sunucusu acmak icin Minecraft EULA'yi kabul etmen gerekiyor:"
  echo "  https://aka.ms/MinecraftEULA"
  answer=""
  read -r -p "EULA'yi okudum ve kabul ediyorum [e/h]: " answer || true
  case "$answer" in
    [eE]*) echo "eula=true" > eula.txt ;;
    *) echo "EULA kabul edilmeden sunucu acilamaz."; exit 1 ;;
  esac
fi

echo
echo "Sunucu baslatiliyor... Kapatmak icin konsola \"stop\" yaz."
exec java -Xms"$RAM" -Xmx"$RAM" \
  -XX:+IgnoreUnrecognizedVMOptions -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
  -jar server.jar --nogui
