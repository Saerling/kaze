# Kaze para Android

App en **Kotlin + Jetpack Compose** con **GeckoView**, el motor de Firefox para Android.
No hace falta compilar Firefox: GeckoView se descarga como librería desde el Maven de Mozilla.

## Probarla

**Opción 1 — descargar el APK:** en GitHub, pestaña *Actions* → último run de *Android* →
sección *Artifacts* → `kaze-arm64-v8a` (casi todos los celulares). Descomprime e instala el `.apk`.

**Opción 2 — Android Studio:** abre la carpeta `android/` y dale ▶ *Run*.

**Opción 3 — terminal:**
```bash
cd android
./gradlew assembleDebug        # APKs en app/build/outputs/apk/debug/
```

Requisitos: JDK 17+ y el Android SDK con la plataforma **37.2** (Android Studio la instala sola si se la pide).

## Qué hace hoy

- Barra de dirección abajo (al alcance del pulgar): escribe una URL o busca en DuckDuckGo
- Pestañas: abrir, cerrar, cambiar; los enlaces "abrir en pestaña nueva" funcionan
- Las pestañas se guardan al cerrar la app y se restauran al volver (solo se carga la visible)
- Bloqueo de rastreadores, cripto-mineros y fingerprinting
- Puede ser tu navegador predeterminado
- Tema claro/oscuro con la paleta de Kaze

## Cómo ahorra RAM

- **Máximo 3 pestañas con el motor cargado** (`TabManager.MAX_LIVE_SESSIONS`). Las demás se *descargan*:
  se guarda su estado (URL, historial, scroll) y se libera la memoria. En la lista de pestañas,
  el punto azul = cargada, gris = descargada.
- Solo la pestaña visible está activa; las otras quedan pausadas y su audio/video se suspende.
- Cuando Android avisa que falta memoria, Kaze descarga todo menos la pestaña que estás viendo.
- Las librerías nativas van sin comprimir dentro del APK: Android las usa directamente sin copiarlas,
  lo que ahorra espacio instalado y RAM (por eso el APK pesa ~185 MB pero no ocupa más al instalarse).

## Estructura

```
app/src/main/java/app/kaze/browser/
├── KazeApp.kt          GeckoRuntime único + reacción a memoria baja
├── MainActivity.kt     abre enlaces externos, guarda pestañas al salir
├── UrlUtils.kt         URL vs. búsqueda
├── tabs/
│   ├── Tab.kt          una pestaña (viva o descargada)
│   └── TabManager.kt   lista de pestañas, límite de memoria, guardar/restaurar
└── ui/
    ├── BrowserScreen.kt  pantalla principal, barra inferior, lista de pestañas
    └── theme/Theme.kt    colores de shared/design/tokens.json
```

## Versión del motor

`gradle/libs.versions.toml` → `geckoview`. Conviene que coincida con la versión de Firefox de Kaze escritorio
(Zen 1.23.x = Firefox 158). Versiones disponibles:
https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/
