#!/usr/bin/env bash
# Limpieza de la TV Hisense 55Q6N (Google TV) por ADB desde el PC.
# Uso:   bash debloat/hisense.sh 192.168.1.49
#
# - Solo DESACTIVA paquetes (pm disable-user). No borra nada: todo se puede
#   volver a activar con debloat/deshacer.sh.
# - La lista sale de paquetes que la comunidad reportó como seguros en TV
#   Hisense con Google TV (catálogo dumbtv) y de su lista general para Google TV.
# - Apaga el protector de pantalla para que no corte el cable.
# - El Wi-Fi puede dejar de funcionar: la TV se usará siempre por Ethernet.
set -u

IP="${1:-192.168.1.49}"
DIR="$(cd "$(dirname "$0")" && pwd)"
REGISTRO="$DIR/registro-hisense.txt"
APK="$DIR/../inicio.apk"

# Nunca se tocan (romperían la TV, los ajustes o la entrada de cable/HDMI)
NUNCA=(
  com.android.tv.settings com.android.settings com.android.systemui
  com.android.packageinstaller com.android.permissioncontroller
  com.android.tv com.google.android.tv.remote.service
  com.mediatek.wwtv.tvcenter com.mediatek.tvinput com.mediatek.tv.service
  com.hisense.tv.ota com.hisense.remoteupgrade
)

# 1) Publicidad, telemetría y promociones (lista de la comunidad)
SEGUROS=(
  com.android.adservices.api                   # anuncios de Privacy Sandbox
  com.android.ondevicepersonalization.services # personalización de anuncios
  com.android.federatedcompute.services        # aprendizaje para anuncios
  com.google.android.feedback                  # envío de errores a Google
  com.android.tv.feedbackconsent               # consentimiento de reportes
  com.google.android.partnersetup              # rastreo de fabricante
  com.google.android.apps.tv.dreamx            # protector de pantalla de Google TV (promos)
  com.google.android.play.games                # Play Juegos
  com.hisense.storemode                        # modo tienda/demostración
  fusion.android.tv.demo                       # video de demostración
  com.hisense.service.message                  # avisos promocionales de Hisense
  com.hisense.alexa                            # Alexa
)

# 2) Apps preinstaladas que no usas (se saltan si no están)
NO_USADAS=(
  com.amazon.amazonvideo.livingroom   # Prime Video
  com.wbd.stream                      # Max (HBO)
  com.iqiyi.i18n.tv                   # iQIYI
  com.disney.disneyplus               # Disney+
  com.google.android.youtube.tvmusic  # YouTube Music
)

adb_tv() { adb -s "$IP:5555" "$@"; }

es_protegido() { local p; for p in "${NUNCA[@]}"; do [[ "$p" == "$1" ]] && return 0; done; return 1; }

desactivar() {
  local p="$1"
  if es_protegido "$p"; then echo "  · $p está protegido, no se toca"; return; fi
  if ! grep -qx "package:$p" <<<"$INSTALADOS"; then echo "  · $p no está en esta TV"; return; fi
  if adb_tv shell pm disable-user --user 0 "$p" >/dev/null 2>&1; then
    echo "  ✓ $p"
    grep -qx "$p" "$REGISTRO" 2>/dev/null || echo "$p" >>"$REGISTRO"
  else
    echo "  ✗ $p (no se pudo)"
  fi
}

preguntar() { local r; read -r -p "$1 [s/N] " r; [[ "$r" =~ ^[sS]$ ]]; }

echo "Conectando con la TV en $IP…"
adb connect "$IP:5555" >/dev/null
if ! adb_tv get-state >/dev/null 2>&1; then
  echo "No hay conexión. Revisa que la depuración USB esté activa y acepta el aviso en la TV."
  exit 1
fi
INSTALADOS="$(adb_tv shell pm list packages)"
echo "Conectado: $(adb_tv shell getprop ro.product.model | tr -d '\r')"

echo
echo "1) Publicidad, telemetría y promociones"
for p in "${SEGUROS[@]}"; do desactivar "$p"; done

echo
echo "2) Apps que no usas"
for p in "${NO_USADAS[@]}"; do desactivar "$p"; done

echo
if preguntar "¿Quitar el Asistente de Google? (el botón de micrófono del control dejará de funcionar)"; then
  desactivar com.google.android.katniss
fi

echo
echo "3) Protector de pantalla apagado (para que no corte el cable)"
adb_tv shell settings put secure screensaver_enabled 0
adb_tv shell settings put secure screensaver_activate_on_sleep 0
adb_tv shell settings put secure screensaver_activate_on_dock 0
echo "  ✓ listo"

echo
echo "4) Launcher propio"
if [[ -f "$APK" ]]; then
  if adb_tv install -r "$APK" >/dev/null; then
    echo "  ✓ instalado"
    adb_tv shell cmd package set-home-activity com.geol.inicio/.MainActivity >/dev/null 2>&1
    if preguntar "¿Desactivar el inicio de Google TV para que quede solo el tuyo?"; then
      desactivar_launcher() {
        adb_tv shell pm disable-user --user 0 "$1" >/dev/null 2>&1 && echo "  ✓ $1" &&
          { grep -qx "$1" "$REGISTRO" 2>/dev/null || echo "$1" >>"$REGISTRO"; }
      }
      desactivar_launcher com.google.android.tungsten.setupwraith
      desactivar_launcher com.google.android.apps.tv.launcherx
    fi
  else
    echo "  ✗ no se pudo instalar $APK"
  fi
else
  echo "  · No encontré inicio.apk junto a esta carpeta; se instala después."
fi

echo
echo "Desactivados guardados en: $REGISTRO"
echo "Para deshacer todo: bash debloat/deshacer.sh $IP"
echo "Reinicia la TV para terminar: adb -s $IP:5555 reboot"
