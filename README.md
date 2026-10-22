# TVHome: launcher para Android TV

Launcher alternativo para Android TV hecho con Jetpack Compose y `androidx.tv:tv-material`.
Pantalla de inicio compacta con apps como banners con efecto neón, entradas HDMI, modo reposo,
administrador de apps en segundo plano y una sección de **canales** que mezcla los de la antena
(sintonizador de la TV) con listas IPTV.

> English: a custom Android TV home screen (Kotlin + Compose for TV) with banner-style app cards, HDMI inputs,
> a rest mode, a background-apps manager and a channels screen that mixes over-the-air channels with IPTV (M3U)
> playlists. UI strings are in Spanish. Setup steps below are language-agnostic (they are mostly shell commands).

Desarrollado y probado en una TCL con Android TV 11. En otras marcas puede haber diferencias
(ver [Limitaciones](#limitaciones-conocidas)).

## Qué incluye

- **Inicio**
  - Fondo de fotos que cambia cada 10 minutos, saludo, hora y fecha.
  - Filas: *Streaming* (apps ancladas), *Entradas* (HDMI y TV en vivo), *Recomendadas* (las que más abres a
    esa hora del día), *Apps* y *Sistema* (Procesos, Almacenamiento, Aplicaciones, Pantalla, Bluetooth).
  - Apps como **banners** (usa el banner de Android TV de la app; si no lo tiene arma uno con su ícono) con
    borde y brillo neón al enfocar.
  - Mantén **OK** sobre una app para abrir su información (ahí puedes desinstalarla).
  - Mantén **OK** sobre una entrada HDMI para cambiarle el nombre (Decodificador, Consola, PC…).
- **Barra superior**: avatar de tu cuenta de Google, pestañas *Inicio · En vivo · Canales · Apps · Procesos* e iconos de
  **Reposo**, **Wi-Fi** (muestra la intensidad de la señal), **launcher original** y **Ajustes**.
- **Reposo**: pantalla casi negra con reloj y fecha tenues que se mueven despacio (cuida el panel). Cualquier tecla
  despierta. No apaga la TV de verdad.
- **Procesos**: cierra apps usadas recientemente para liberar RAM (necesita un permiso, ver más abajo).
- **Canales**: vista previa del canal a la izquierda y lista a la derecha, con filtros *Todos / Antena / IPTV / Deportes*.
  - **OK** sobre un canal lo reproduce; **OK** sobre el video lo pasa a pantalla completa.
  - **CH+ / CH−** del control cambian de canal; en pantalla completa también **arriba / abajo**.
  - Si un canal no responde muestra *Fuera de servicio* (OK reintenta). Mientras carga, un spinner pequeño.

## Requisitos

| Para | Necesitas |
|---|---|
| Compilar | [Android Studio](https://developer.android.com/studio) reciente, **JDK 17** y el **Android SDK Platform 34** |
| Instalar | `adb` (viene con Android Studio, en *platform-tools*) |
| Usar | Una TV con **Android TV / Google TV, Android 11 o superior** (`minSdk 30`) y la depuración por USB/red activada |

Versiones usadas: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 9.3.0 (wrapper incluido), Compose BOM 2024.10.01,
`tv-material` 1.0.0 y Media3 1.4.1.

## Cómo replicarlo

### 1. Clonar y compilar

```bash
git clone https://github.com/8Wada/TVHome.git
cd TVHome
./gradlew assembleRelease
```

Android Studio crea `local.properties` con la ruta de tu SDK la primera vez que abres el proyecto. Si compilas
solo por terminal, créalo tú: `echo "sdk.dir=/ruta/a/Android/sdk" > local.properties`.

El APK queda en `app/build/outputs/apk/release/app-release.apk`. Compila **release**: el modo debug de Compose es
mucho más lento en una TV.

> El build *release* se firma con la llave **debug** (ver `app/build.gradle`) para poder instalarlo por `adb` sin
> configurar nada. Si vas a distribuir el APK, configura tu propia firma.

### 2. Activar `adb` en la TV

1. *Ajustes → Sistema → Acerca de*: pulsa **Compilación** 7 veces para activar las opciones de desarrollador.
2. *Ajustes → Sistema → Opciones de desarrollador*: activa **Depuración USB** o **Depuración por red**.
3. Conecta desde tu computadora (TV y computadora en la misma red):

```bash
adb connect IP_DE_LA_TV:5555
```

### 3. Instalar y dar permisos

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n app.tvhome.launcher/.HomeActivity
```

Permisos opcionales, uno por función:

| Función | Comando |
|---|---|
| Avatar con tu cuenta de Google | `adb shell pm grant app.tvhome.launcher android.permission.GET_ACCOUNTS` |
| Pestaña *Procesos* (apps recientes) | `adb shell appops set app.tvhome.launcher GET_USAGE_STATS allow` |
| Canales de **antena** | `adb shell pm grant app.tvhome.launcher android.permission.READ_TV_LISTINGS` |

Si la TV no deja leer tu cuenta, pásala a mano:
`adb shell am start -n app.tvhome.launcher/.HomeActivity --es account tu@correo.com`.

Para los canales de antena, antes escanea los canales desde los ajustes de la TV: la app solo lista los que el
sistema ya tiene guardados.

### 4. Hacerlo el launcher predeterminado

```bash
adb shell cmd package set-home-activity app.tvhome.launcher/.HomeActivity
```

Si el botón **Home** sigue abriendo el launcher de fábrica, desactívalo (el paquete cambia según la TV):

```bash
adb shell pm list packages | grep -i launcher            # busca el de tu TV
adb shell pm disable-user --user 0 com.google.android.tvlauncher            # Android TV
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx    # Google TV
```

Con el original desactivado, el icono *launcher original* de la barra superior dejará de funcionar.
**Prueba primero solo con `set-home-activity`**, que es reversible.

### 5. Revertir

```bash
adb shell pm enable com.google.android.tvlauncher            # o el paquete que hayas desactivado
adb uninstall app.tvhome.launcher
```

## Personalización

Todo está en `app/src/main/java/app/tvhome/launcher/`.

| Qué | Dónde |
|---|---|
| Apps que salen primero en *Streaming* | lista `PINNED` (nombres de paquete) en `HomeActivity.kt` |
| Apps que nunca se ofrecen para cerrar | `PROTECTED` en `HomeActivity.kt` |
| País de los canales IPTV | `IPTV_COUNTRY` en `Channels.kt` (código de 2 letras: `mx`, `us`, `es`, `ar`…) |
| Listas IPTV por defecto | `DEFAULT_SOURCES` en `Channels.kt` |
| Añadir otra lista sin recompilar | `adb shell am start -n app.tvhome.launcher/.HomeActivity --es iptv_url "https://…/lista.m3u"` |
| Zona horaria del reloj (si la de tu TV está mal) | `CLOCK_TZ` en `HomeActivity.kt`; por defecto usa la del sistema |
| Nombres que rota la pulsación larga en una entrada HDMI | `INPUT_NAMES` en `HomeActivity.kt` |
| Logotipo de una entrada HDMI | pon `app/src/main/res/drawable/logo_<nombre>.png` con el nombre de la entrada en minúsculas y solo letras y números (ej. `logo_consola.png` para "Consola", `logo_bluray.png` para "Blu-ray") y dale ese nombre a la entrada |
| Fotos de fondo | 12 archivos `app/src/main/assets/wallpapers/01.jpg … 12.jpg` (si cambias la cantidad, ajusta `WALLPAPERS`) |
| Textos de la interfaz | están en español directamente en el código (aún no hay `strings.xml` por idioma) |

## Canales: antena e IPTV

- **Antena**: se leen de la base de datos de canales del sistema (`TvContract`) y se reproducen con `TvView`.
  Necesita el permiso `READ_TV_LISTINGS` y haber escaneado canales en la TV.
- **IPTV**: la app descarga las listas M3U, las guarda en el almacenamiento interno y las reproduce con
  ExoPlayer (HLS y TS). Se actualizan con el icono de refrescar. Los canales repetidos entre listas se descartan.
- Las listas públicas suelen traer canales caídos o con bloqueo geográfico; es normal que algunos marquen
  *Fuera de servicio*.

## Solución de problemas

| Síntoma | Qué probar |
|---|---|
| Un canal IPTV marca *Fuera de servicio* | Lee el código de error en letra chica del aviso, o mira el log: `adb logcat -d \| grep TVHome` |
| No salen canales de antena | Falta el permiso `READ_TV_LISTINGS` o no se han escaneado canales en la TV |
| El video de antena no se ve o sale en otro lugar | Ver *Limitaciones* |
| *Procesos* pide permiso | Ejecuta el comando de `GET_USAGE_STATS` de la tabla de permisos |
| La hora o el saludo no coinciden con tu hora | La zona horaria de la TV está mal; corrígela en la TV o fija `CLOCK_TZ` |
| Me quedé sin launcher | `adb shell pm enable <paquete del launcher original>` |

## Limitaciones conocidas

- Probado solo en una TCL con Android TV 11. Otras marcas pueden comportarse distinto.
- En esa TV, el video de **antena** lo dibuja el sistema en su propia capa **desde la esquina superior izquierda** de la
  pantalla y no respeta márgenes; por eso la vista previa de Canales está pegada a esa esquina. Otra TV podría
  necesitar otro acomodo.
- *Reposo* no apaga la TV; solo oscurece la pantalla.
- *Procesos* se basa en el historial de uso de apps (Android no deja a una app ver los procesos de otras).
- La interfaz está solo en español.

## Aviso sobre IPTV

Esta app **no incluye ninguna transmisión ni lista de canales**: solo reproduce las listas M3U que tú cargues
(por defecto, las públicas de [iptv-org](https://github.com/iptv-org/iptv), que se descargan al usarla). Tú eres
responsable de que el contenido que reproduzcas sea legal en tu país. Los canales y sus logotipos pertenecen a sus dueños.

## Licencias

- **Código, banner e ícono de la app**: [MIT](LICENSE).
- **Terceros** (fuente Poppins, fotos de fondo, dependencias): ver [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
  Las fotos de fondo son CC BY-SA y su atribución está en [`app/src/main/assets/wallpapers/CREDITS.md`](app/src/main/assets/wallpapers/CREDITS.md).
- Los nombres de servicios que aparecen en `PINNED` son solo identificadores de paquete de Android y marcas de sus
  dueños; este proyecto no tiene relación con ellos.
