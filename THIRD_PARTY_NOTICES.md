# Avisos de terceros

Este proyecto (código, banner e ícono) se distribuye bajo la licencia MIT (ver `LICENSE`).
Lo siguiente es de terceros y conserva su propia licencia.

## Incluido en este repositorio

| Elemento | Autor | Licencia | Dónde |
|---|---|---|---|
| Fuente **Poppins** (Regular, Medium, Bold) | The Poppins Project Authors, <https://github.com/itfoundry/Poppins> | SIL Open Font License 1.1 | `app/src/main/res/font/`; texto completo en `licenses/Poppins-OFL.txt` |
| **Fotos de fondo** (12 imágenes reducidas a 1280x720) | Varios autores de Wikimedia Commons | Creative Commons BY-SA 3.0 / 4.0, según la foto | `app/src/main/assets/wallpapers/`; autor, licencia y enlace de cada una en `CREDITS.md` de esa carpeta |
| **Gradle Wrapper** | Gradle Inc. | Apache License 2.0 | `gradle/wrapper/`, `gradlew`, `gradlew.bat` |

Las fotos se tomaron del proyecto <https://github.com/mihirgupta0900/tv-home> (código MIT), que a su vez las
obtuvo de Wikimedia Commons. La licencia CC BY-SA de las fotos aplica a las imágenes, no al código. Si
redistribuyes la app con las fotos, conserva la atribución de `CREDITS.md`; si no quieres esa condición,
reemplaza las fotos por las tuyas (ver el README).

## Dependencias (se descargan al compilar; no están en el repositorio)

| Dependencia | Licencia |
|---|---|
| Kotlin y plugin de Compose | Apache License 2.0 |
| Jetpack Compose (BOM 2024.10.01), AndroidX Activity, ProfileInstaller | Apache License 2.0 |
| AndroidX TV Material (`androidx.tv:tv-material`) | Apache License 2.0 |
| AndroidX Media3 ExoPlayer y HLS (`androidx.media3`) | Apache License 2.0 |
| Android Gradle Plugin | Apache License 2.0 |

## Datos que se cargan en tiempo de ejecución

- **Listas IPTV (M3U)** de [iptv-org](https://github.com/iptv-org/iptv): la app no incluye ninguna lista ni
  transmisión; las descarga cuando la usas. Revisa el aviso legal y la licencia de ese proyecto.
  Los canales y sus logotipos pertenecen a sus respectivos dueños.

## Marcas

Los nombres de servicios (por ejemplo, de plataformas de streaming) aparecen solo como identificadores de
paquete de Android en la lista de apps ancladas del código, para poder mostrarlas primero. Son marcas de sus
dueños; este proyecto no tiene relación con ellos.
