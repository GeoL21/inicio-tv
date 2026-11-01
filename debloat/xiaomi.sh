#!/usr/bin/env bash
# Limpieza de la Xiaomi TV Box S (3.ª gen., Google TV) por ADB desde el PC.
# Uso:   bash debloat/xiaomi.sh 192.168.1.9
#
# - Solo DESACTIVA paquetes (pm disable-user). No borra nada: todo se puede
#   volver a activar con debloat/deshacer.sh 192.168.1.9.
# - Antes de tocar nada, muestra la lista de lo que va a desactivar y pide confirmación.
# - La lista sale de lo que la comunidad reportó como seguro en Google TV y en
#   equipos Xiaomi (catálogo dumbtv). No toca nada de red: el Wi-Fi sigue funcionando.
set -u

IP="${1:-192.168.1.9}"
DIR="$(cd "$(dirname "$0")" && pwd)"
REGISTRO="$DIR/registro-xiaomi.txt"
APK="$DIR/../inicio.apk"

# Nunca se tocan (romperían la TV box, los ajustes, la red o el control)
NUNCA=(
  com.android.tv.settings com.android.settings com.android.systemui
  com.android.packageinstaller com.android.permissioncontroller
  com.google.android.tv.remote.service com.google.android.gms com.android.vending
  com.android.networkstack com.android.wifi.resources com.android.bluetooth
)

# 1) Publicidad, telemetría y promociones (lista general de la comunidad para Google TV)
SEGUROS=(
  com.android.adservices.api                   # anuncios de Privacy Sandbox
  com.android.ondevicepersonalization.services # personalización de anuncios
  com.android.federatedcompute.services        # aprendizaje para anuncios
  com.google.android.feedback                  # envío de errores a Google
  com.android.tv.feedbackconsent               # consentimiento de reportes
  com.google.android.partnersetup              # rastreo de fabricante
  com.google.android.apps.tv.dreamx            # protector de Google TV con promociones
  com.google.android.play.games                # Play Juegos
)

# 2) Apps de Xiaomi y preinstaladas que no usas (se saltan si no están)
NO_USADAS=(
  com.mitv.tvhome.atv                 # PatchWall (probado por la comunidad en Xiaomi)
  com.amazon.amazonvideo.livingroom   # Prime Video
  com.wbd.stream                      # Max (HBO)
  com.iqiyi.i18n.tv                   # iQIYI
  com.disney.disneyplus               # Disney+
  com.google.android.youtube.tvmusic  # YouTube Music
)

adb_tv() { adb -s "$IP:5555" "$@"; }
es_protegido() { local p; for p in "${NUNCA[@]}"; do [[ "$p" == "$1" ]] && return 0; done; return 1; }
preguntar() { local r; read -r -p "$1 [s/N] " r; [[ "$r" =~ ^[sS]$ ]]; }

desactivar() {
  local p="$1"
  if es_protegido "$p"; then echo "  · $p está protegido, no se toca"; return; fi
  if adb_tv shell pm disable-user --user 0 "$p" >/dev/null 2>&1; then
    echo "  ✓ $p"
    grep -qx "$p" "$REGISTRO" 2>/dev/null || echo "$p" >>"$REGISTRO"
  else
    echo "  ✗ $p (no se pudo)"
  fi
}

echo "Conectando con la TV box en $IP…"
adb connect "$IP:5555" >/dev/null
if ! adb_tv get-state >/dev/null 2>&1; then
  echo "No hay conexión. Revisa que la depuración USB esté activa y acepta el aviso en la TV."
  exit 1
fi
INSTALADOS="$(adb_tv shell pm list packages -e)"   # solo los que están activos
echo "Conectado: $(adb_tv shell getprop ro.product.model | tr -d '\r')"

# Vista previa: qué se va a desactivar en ESTE equipo
A_DESACTIVAR=()
for p in "${SEGUROS[@]}" "${NO_USADAS[@]}"; do
  grep -qx "package:$p" <<<"$INSTALADOS" && A_DESACTIVAR+=("$p")
done
echo
if [[ ${#A_DESACTIVAR[@]} -eq 0 ]]; then
  echo "No hay nada de la lista activo en este equipo (quizá ya se limpió antes)."
else
  echo "Se van a desactivar ${#A_DESACTIVAR[@]} paquetes:"
  printf '  - %s\n' "${A_DESACTIVAR[@]}"
  echo
  if preguntar "¿Continuar?"; then
    for p in "${A_DESACTIVAR[@]}"; do desactivar "$p"; done
  else
    echo "  No se desactivó nada."
  fi
fi

echo
if grep -qx "package:com.google.android.katniss" <<<"$INSTALADOS" &&
   preguntar "¿Quitar el Asistente de Google? (el botón de micrófono del control dejará de funcionar)"; then
  desactivar com.google.android.katniss
fi

echo
echo "Launcher propio"
if [[ -f "$APK" ]]; then
  if adb_tv install -r "$APK" >/dev/null; then
    echo "  ✓ instalado"
    adb_tv shell cmd package set-home-activity com.geol.inicio/.MainActivity >/dev/null 2>&1
    if preguntar "¿Desactivar el inicio de Google TV para que quede solo el tuyo?"; then
      for p in com.google.android.tungsten.setupwraith com.google.android.apps.tv.launcherx com.google.android.tvlauncher; do
        grep -qx "package:$p" <<<"$INSTALADOS" && desactivar "$p"
      done
    fi
  else
    echo "  ✗ no se pudo instalar $APK"
  fi
else
  echo "  · No encontré inicio.apk junto a esta carpeta; descárgalo primero (ver README)."
fi

echo
echo "Desactivados guardados en: $REGISTRO"
echo "Para deshacer todo: bash debloat/deshacer.sh $IP"
echo "Reinicia la TV box para terminar: adb -s $IP:5555 reboot"
