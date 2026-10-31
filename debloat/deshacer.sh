#!/usr/bin/env bash
# Vuelve a activar todo lo que desactivó hisense.sh (o xiaomi.sh) y el protector.
# Uso:   bash debloat/deshacer.sh 192.168.1.49
set -u
IP="${1:-192.168.1.49}"
DIR="$(cd "$(dirname "$0")" && pwd)"
case "$IP" in
  192.168.1.9) REGISTRO="$DIR/registro-xiaomi.txt" ;;
  *)           REGISTRO="$DIR/registro-hisense.txt" ;;
esac

adb connect "$IP:5555" >/dev/null
if ! adb -s "$IP:5555" get-state >/dev/null 2>&1; then
  echo "No hay conexión con $IP."; exit 1
fi
if [[ ! -s "$REGISTRO" ]]; then echo "No hay nada que deshacer ($REGISTRO vacío)."; exit 0; fi

# El inicio de Google TV primero, para que la TV nunca quede sin pantalla de inicio
for p in com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith; do
  grep -qx "$p" "$REGISTRO" && adb -s "$IP:5555" shell pm enable --user 0 "$p" >/dev/null && echo "  ✓ $p"
done
while read -r p; do
  [[ -z "$p" ]] && continue
  adb -s "$IP:5555" shell pm enable --user 0 "$p" >/dev/null 2>&1 && echo "  ✓ $p" || echo "  ✗ $p"
done <"$REGISTRO"

read -r -p "¿Volver a encender el protector de pantalla? [s/N] " r
[[ "$r" =~ ^[sS]$ ]] && adb -s "$IP:5555" shell settings put secure screensaver_enabled 1

mv "$REGISTRO" "$REGISTRO.deshecho"
echo "Listo. Reinicia el equipo: adb -s $IP:5555 reboot"
