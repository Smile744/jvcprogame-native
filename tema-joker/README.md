# TEMA JOKER · JVCSMILE para Infinix GT 30 Pro (X6873)

Paquete de fondos para personalizar el **teléfono completo**, no una app.
Pensado para XOS con modo oscuro prendido (`ui_night_mode = 2`).

## Los dos fondos

| Archivo | Cuándo usarlo | Qué hace |
|---|---|---|
| `wallpapers/joker-azul-frio.png` | teléfono frío / jugando tranquilo | el sistema toma acento **azul** |
| `wallpapers/joker-rojo-caliente.png` | el teléfono se calentó o bajan los FPS | el sistema toma acento **rojo** |

Ambos son PNG de 1024 × 1792, sin texto ni marca de agua, con la parte superior
oscura para que el reloj del bloqueo se lea bien.

## Cómo ponerlo (2 minutos, sin PC y sin root)

1. Descargá el PNG que quieras (enlace directo abajo).
2. Abrí la **Galería** → carpeta **Descargas** → elegí la imagen.
3. Menú (⋮) → **Establecer como** → **Ambas** (pantalla de inicio y bloqueo).
4. Bajá el panel de notificaciones: los interruptores y Ajustes deberían verse
   del color del fondo.

Para cambiar de estado, repetís 1-3 con el otro PNG. Cuesta unos 5 segundos.

## Enlaces de descarga

- Azul: `https://raw.githubusercontent.com/Smile744/jvcprogame-native/main/tema-joker/wallpapers/joker-azul-frio.png`
- Rojo: `https://raw.githubusercontent.com/Smile744/jvcprogame-native/main/tema-joker/wallpapers/joker-rojo-caliente.png`
- Todo junto (ZIP del repo): `https://github.com/Smile744/jvcprogame-native/archive/refs/heads/main.zip`

## Qué hace este tema y qué no

**Sí:** cambia el fondo de inicio y de bloqueo, y —como XOS tiene los overlays de
color dinámico de SystemUI encendidos (`com.android.systemui:accent` y `:dynamic`)—
arrastra el color de acento de Ajustes, panel rápido y notificaciones.

**No:** no cambia de color solo. Un fondo es una imagen quieta; leer la
temperatura necesita un proceso que esté corriendo con permisos, y eso ya no es
un tema sino una app. Acá lo cambiás vos cuando te conviene.

**Tampoco** toca íconos, fuentes ni sonidos: son otra capa del sistema y se
cambian por separado (forma de ícono con los overlays que el ROM ya trae).
