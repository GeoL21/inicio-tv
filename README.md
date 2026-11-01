# Inicio TV

Pantalla de inicio propia, simple y liviana, para la TV Hisense 55Q6N y la Xiaomi TV Box S.
Hecha desde cero por GeoL, con ayuda de Claude.

- Reloj grande, fecha y saludo (buenos días / tardes / noches)
- Clima de Barranquilla (Open-Meteo, sin cuenta ni clave)
- Accesos rápidos: fondos, red, día/noche, entradas HDMI (solo si el equipo las tiene) y ajustes
- **Día / noche:** de 6 p. m. a 6 a. m. los iconos pasan a círculos oscuros. El botón recorre
  Automático (con una "A") → Noche → Día
- Iconos en **squircle**, en alta resolución. En Fondos eliges **Originales** (colores de cada app)
  o **Temáticos** (estilo Material You: un solo tono sacado del fondo de pantalla, claro de día y oscuro de noche)
- El dock y los botones de arriba toman un velo muy suave del color del fondo
- Dock centrado de hasta **8 apps**: los iconos se achican solos a medida que agregas apps.
  Los nombres largos se cortan por palabras ("Xuper Crystal Clear" → "Xuper")
- Acceso directo **Canales TV** (entra directo al cable) y panel de entradas HDMI con las conectadas
- **Flecha abajo** abre todas las apps; flecha arriba o Atrás para volver.
  Ahí, mantener OK fija o quita una app del inicio
- Para ordenar la fila: mantén OK sobre una app, muévela con ← → y pulsa OK
- Selector de fondos estáticos (5 incluidos + los tuyos), sin videos ni animaciones pesadas
- Fuente Google Sans Flex (licencia OFL, incluida)
- Sin librerías externas: el APK pesa poco y gasta poca memoria

## 1. Obtener el APK

Cada vez que se sube un cambio a `main`, GitHub compila el APK y lo publica en
**Releases** como `inicio.apk`. Descárgalo y ponlo en la carpeta del proyecto
(al lado de `debloat/`).

## 2. Limpiar la TV Hisense e instalar el inicio

En la TV: Ajustes → Sistema → Información → pulsa 7 veces sobre la compilación,
luego Opciones de desarrollador → Depuración USB: activada.

Desde el PC (funciona igual en fish):

```
bash debloat/hisense.sh 192.168.1.49
```

El script:

1. Desactiva publicidad, telemetría y promociones (paquetes que la comunidad
   reportó como seguros en Hisense con Google TV).
2. Desactiva apps preinstaladas que no se usan (Prime Video, Max, iQIYI, Disney+, YouTube Music, Alexa).
3. Pregunta si quitar el Asistente de Google.
4. **Apaga el protector de pantalla**, que era lo que cortaba el cable al rato.
5. Instala `inicio.apk`, lo deja como inicio y pregunta si desactivar el de Google TV.

Nunca toca los ajustes, la entrada de cable/HDMI ni las actualizaciones de Hisense.
Todo queda anotado en `debloat/registro-hisense.txt`.

> El Wi-Fi de la Hisense puede dejar de funcionar. Se usa siempre por Ethernet.

## Limpiar la TV box Xiaomi

Misma idea, con su propia lista (incluye PatchWall) y sin tocar nada de red:

```
bash debloat/xiaomi.sh 192.168.1.9
```

Antes de cambiar algo muestra qué va a desactivar y pide confirmación.

## Deshacer

```
bash debloat/deshacer.sh 192.168.1.49   # TV Hisense
bash debloat/deshacer.sh 192.168.1.9    # TV box Xiaomi
```

## Fondos de pantalla

El botón de imagen (arriba, junto a red) abre **Fondos**: miniaturas de los 5 fondos
incluidos y de los tuyos. OK aplica el fondo al instante; el actual tiene una marca.

Para agregar fotos propias (JPG, PNG o WEBP; 1920×1080 es suficiente):

```
adb -s 192.168.1.49:5555 shell mkdir -p /sdcard/Android/data/com.geol.inicio/files/fondos
adb -s 192.168.1.49:5555 push playa.jpg /sdcard/Android/data/com.geol.inicio/files/fondos/
```

El nombre del archivo es el que se ve en la miniatura. Para quitar una:
`adb -s 192.168.1.49:5555 shell rm /sdcard/Android/data/com.geol.inicio/files/fondos/playa.jpg`

## Iconos propios (opcional)

Si el icono de una app no te gusta, copia un PNG cuadrado (512×512 es ideal) con el
nombre del paquete de la app. Llena todo el círculo:

```
adb -s 192.168.1.49:5555 shell mkdir -p /sdcard/Android/data/com.geol.inicio/files/iconos
adb -s 192.168.1.49:5555 push youtube.png /sdcard/Android/data/com.geol.inicio/files/iconos/com.google.android.youtube.tv.png
```

Para ver el nombre del paquete de tus apps: `adb -s 192.168.1.49:5555 shell pm list packages -3`.
Se aplica al volver al inicio. Para quitarlo, borra el archivo.

## Créditos

- Lista de paquetes seguros: catálogo de la comunidad [dumbtv](https://github.com/humblemuzzu/dumbtv).
- Fuente [Google Sans Flex](https://github.com/googlefonts/googlesans-flex) (licencia OFL, ver `LICENCIA-FUENTE-GOOGLE-SANS-FLEX.txt`).
- Clima: [Open-Meteo](https://open-meteo.com).
