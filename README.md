# Inicio TV

Pantalla de inicio propia, simple y liviana, para la TV Hisense 55Q6N y la Xiaomi TV Box S.
Hecha desde cero por GeoL, con ayuda de Claude.

- Reloj grande, fecha y saludo (buenos días / tardes / noches)
- Clima de Barranquilla (Open-Meteo, sin cuenta ni clave)
- Accesos rápidos: red, entrada de TV (solo si el equipo la tiene) y ajustes
- Una sola fila de apps: Netflix, Play Store y **Todas las apps**
- En **Todas las apps**: OK abre, mantener OK fija o quita la app del inicio
- Fondo estático, fuente Inter, sin videos ni animaciones pesadas
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

## Cambiar el fondo

Usa una imagen JPG horizontal (1920×1080 es suficiente):

```
adb -s 192.168.1.49:5555 push mi-foto.jpg /sdcard/Android/data/com.geol.inicio/files/fondo.jpg
```

Sal al inicio y vuelve a entrar para verla. Si borras el archivo, vuelve el fondo original.

## Créditos

- Lista de paquetes seguros: catálogo de la comunidad [dumbtv](https://github.com/humblemuzzu/dumbtv).
- Fuente [Inter](https://rsms.me/inter/) de Rasmus Andersson (licencia OFL, ver `LICENCIA-FUENTE-INTER.txt`).
- Clima: [Open-Meteo](https://open-meteo.com).
