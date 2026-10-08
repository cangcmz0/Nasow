#!/usr/bin/env bash
# Paper'i ve tum eklentileri en yeni surume gunceller. Sunucu KAPALIYKEN calistir.
set -euo pipefail
cd "$(dirname "$0")"
exec java setup/Setup.java --update
