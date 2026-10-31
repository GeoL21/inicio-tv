# Inicio TV

Pantalla de inicio propia, simple y liviana, para la TV Hisense 55Q6N y la Xiaomi TV Box S.
Hecha desde cero por GeoL, con ayuda de Claude.

- Reloj grande, fecha y saludo (buenos días / tardes / noches)
- Clima de Barranquilla (Open-Meteo, sin cuenta ni clave)
- Accesos rápidos: red, entrada de TV (solo si el equipo la tiene) y ajustes
- Una sola fila de apps: Netflix, Play Store y **Todas las apps**
- En **Todas las apps**: OK abre, mantener OK fija o quita la app del inicio
- Selector de fondos estáticos (5 incluidos + los tuyos), sin videos ni animaciones pesadas
- Fuente Inter, o tu propia fuente si la copias a la TV
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

## Deshacer

```
bash debloat/deshacer.sh 192.168.1.49
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

## Fuente personal (opcional)

La app usa Inter. Si copias tu propia fuente con el nombre `fuente.otf` (o `fuente.ttf`)
a la carpeta de la app, se usa esa. El archivo queda solo en tu TV: no va dentro del APK
ni en este repositorio.

```
adb -s 192.168.1.49:5555 push mi-fuente.otf /sdcard/Android/data/com.geol.inicio/files/fuente.otf
```

Se aplica al volver al inicio. Para volver a Inter, borra el archivo con `adb shell rm`.

## Créditos

- Lista de paquetes seguros: catálogo de la comunidad [dumbtv](https://github.com/humblemuzzu/dumbtv).
- Fuente [Inter](https://rsms.me/inter/) de Rasmus Andersson (licencia OFL, ver `LICENCIA-FUENTE-INTER.txt`).
- Clima: [Open-Meteo](https://open-meteo.com).
