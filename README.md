# JVC-PRO-GAME (nativo, Kotlin + Shizuku)

Proyecto Android nativo. No necesita PC con Android Studio: GitHub Actions lo compila.

## Que hace hoy

- Overlay flotante con Hz de pantalla y FPS reales (via `dumpsys gfxinfo`) del juego elegido.
- DNS privado: AdGuard (bloquea anuncios), Cloudflare, Quad9, o apagarlo.
- Densidad (`wm density`) y tasa de refresco (`user_refresh_rate`).
- Congelar / descongelar / matar apps instaladas (`pm suspend`, `am force-stop`).
- Lanzar el juego elegido.

**Todo lo que toca el sistema requiere Shizuku activo y con permiso concedido.** Sin Shizuku la app abre y muestra el panel, pero los botones devuelven "Shizuku no esta activo". Shizuku se activa con "Iniciar mediante wireless" en Opciones de desarrollador (Android 11+) o por ADB.

Lo que NO hace, a proposito: inyectar tacto dentro del juego, bajar el ping, ni rutas a Brasil. Eso exige root o un VPS propio y es baneable.

## Arbol de archivos (las rutas son exactas, importan)

```
build.gradle.kts
settings.gradle.kts
gradle.properties
README.md
.github/workflows/build-native-apk.yml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/org/jvc/progame/ShizukuShell.kt
app/src/main/java/org/jvc/progame/JvcCommands.kt
app/src/main/java/org/jvc/progame/FpsOverlayService.kt
app/src/main/java/org/jvc/progame/MainActivity.kt
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/drawable/ic_launcher.xml
```

## Subir a GitHub (sin git en tu PC)

1. `https://github.com/new` -> nombre `jvcprogame-native`, **Public**, **sin** README ni .gitignore -> Create repository.
2. En el repo vacio: **Add file -> Upload files** y sube de una vez los 4 archivos de la raiz (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `README.md`) -> Commit changes.
3. Para el resto, usa **Add file -> Create new file** y escribe la ruta completa en el nombre del archivo; GitHub crea las carpetas solo. Ejemplo exacto:
   `.github/workflows/build-native-apk.yml`
   Pegar el contenido -> Commit changes. Repite con cada ruta de la lista.
4. No hace falta `gradlew` ni `gradle-wrapper.jar`: el workflow instala Gradle 8.7 el mismo.

## Compilar

Actions -> `construir-apk-nativo` -> espera 4-8 min -> **Artifacts: `jvcprogame-apk`** -> dentro esta el `.apk` (debug, firma de pruebas; instalalo solo en tu movil).

Si sale en rojo, mira que paso fallo:
- paso "Compilar" en los primeros 2 min -> un archivo esta en la ruta incorrecta o con nombre equivocado.
- fallo en `assembleDebug` con mensaje de Kotlin -> pega el log y lo arreglamos.
