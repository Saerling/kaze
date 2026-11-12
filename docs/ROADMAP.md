# Plan de trabajo

## Fase 1 — Base desde cero ✅
- [x] Android: espacios, Essentials, fijadas, pestañas que se duermen, búsqueda con sugerencias, historial, privadas, descargas, buscar en página, versión de escritorio, permisos y diálogos de páginas, espacios aislados
- [x] Android: arreglado el cierre al abrir (motor creado en procesos hijos)
- [x] Android: pruebas y capturas automáticas
- [x] Windows: interfaz Fyx sobre las pestañas verticales de Firefox (espacios, Essentials, compacto, página flotante, dormir pestañas)
- [x] Windows: configuración de compilación propia (surfer) con 3 parches y marca Fyx
- [x] Windows: cargador de desarrollo para probar sin compilar
- [x] Windows: estética Safari (barra centrada, menú aA, vista de pestañas en tarjetas, página de inicio, perfiles con contenedores)
- [x] Windows: instalador .exe en Releases armado sobre el Firefox oficial, con identidad propia y actualizaciones desde la app
- [x] CI: APK + pruebas en cada push; comprobación de parches de escritorio
- [x] Android: actualizaciones desde la app (GitHub Releases) con firma fija para instalar encima

## Fase 2 — Primera versión para usar a diario
- [x] Android: páginas de error propias (sin conexión, sitio caído, certificado inválido, sitio peligroso) en vez de pantalla en blanco
- [x] Android: compartir desde las páginas (navigator.share), "Buscar en Fyx" al seleccionar texto en otras apps, video en ventana flotante al salir
- [x] Android: búsqueda por voz y lector de códigos QR en el campo de búsqueda
- [x] Android: notificaciones de los sitios como notificaciones de Android (mientras su pestaña está despierta)
- [x] Android: los complementos solo se instalan desde addons.mozilla.org
- [x] Android: HTTPS primero, enlaces sin códigos de rastreo, prioridad de memoria para la pestaña visible, freno a páginas que se cierran en bucle
- [ ] Windows: primera compilación completa en tu PC y medir RAM contra Firefox y Zen
- [ ] Windows: editor de espacios en un panel (hoy usa menús)
- [ ] Windows: arrastrar pestañas entre espacios y reordenar espacios arrastrando
- [ ] Android: deslizar en la barra para cambiar de pestaña
- [ ] Android: lector, traducir página, compartir hacia un espacio (como Z-Sync)
- [ ] Android: uBlock Origin preinstalado (hoy se instala desde Ajustes)

## Fase 3 — Todo conectado
- [ ] Firefox Sync en Android (historial, marcadores, contraseñas, pestañas)
- [ ] Espacios y Essentials sincronizados entre Windows y Android
- [ ] "Continuar en el otro dispositivo"
- [ ] Windows: poder poner Fyx como navegador predeterminado
