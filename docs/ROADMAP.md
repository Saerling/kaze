# Plan de trabajo

## Fase 0 — Base ✅ (este commit)
- [x] Estructura del repo, licencia MPL-2.0, documentación
- [x] Escritorio: script que descarga Zen y aplica la capa Kaze (nombre, íconos, preferencias)
- [x] Preferencias de bajo consumo de RAM para escritorio (`desktop/prefs/kaze/`)
- [x] Android: app GeckoView con barra de dirección, pestañas y descarga automática de pestañas inactivas
- [x] CI: compilación del APK de Android en cada push

## Fase 1 — Escritorio usable
- [ ] Primera compilación de Kaze para Windows en tu PC
- [ ] Revisar textos que todavía dicen "Zen" en la interfaz y cambiarlos (parches en `desktop/patches/`)
- [ ] Desactivar actualizaciones automáticas de Zen y apuntarlas a los releases de Kaze
- [ ] Tema visual propio de Kaze (usando `shared/design/tokens.json`)
- [ ] Medir RAM vs. Zen y Firefox con 10 / 30 / 60 pestañas

## Fase 2 — Android completo
- [ ] Historial y marcadores locales
- [ ] Descargas, modo privado, bloqueador de anuncios (uBlock Origin vía WebExtensions de GeckoView)
- [ ] Pestañas agrupadas en espacios (como los workspaces de Zen)
- [ ] Integrar Android Components de Mozilla para **Firefox Sync** (historial, marcadores, pestañas, contraseñas)

## Fase 3 — Todo conectado
- [ ] "Continuar en el otro dispositivo": enviar pestaña Windows ⇄ Android
- [ ] Espacios de trabajo sincronizados entre dispositivos
- [ ] Releases automáticos (instalador de Windows + APK firmado)
