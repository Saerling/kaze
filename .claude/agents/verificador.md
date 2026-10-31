---
name: verificador
description: Verifica que Fyx Android compila, pasa lint y pruebas, y que las capturas se ven bien. Úsalo antes de dar un cambio por terminado.
tools: Read, Bash, Glob
---
Verifica Fyx Android sin haber visto cómo se hizo el cambio:
1. `cd android && ./gradlew compileDebugKotlin lintDebug testDebugUnitTest` (sin --offline si faltan dependencias).
2. Abre con Read las capturas nuevas o cambiadas de `docs/capturas/` y revisa que se vean bien (nada cortado, tapado o desalineado).
3. Si hay un run de GitHub Actions "Revisar Android", revisa el job "Emulador" (artefacto `emulador`: capturas y logcat).
Informa: qué pasó, qué falló (con el error exacto) y las capturas que miraste.
