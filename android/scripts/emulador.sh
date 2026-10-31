#!/bin/bash
# Prueba de humo en un emulador Android real (la usa .github/workflows/revisar.yml).
# Instala Fyx, la abre, carga páginas, toca la interfaz al azar y falla si la app se cierra sola.
set -u
PKG=app.kaze.browser.debug
OUT=emulador
mkdir -p $OUT
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
adb logcat -c
adb shell am start -W -n $PKG/app.kaze.browser.MainActivity
sleep 12; adb exec-out screencap -p > $OUT/1-inicio.png
adb shell am start -W -a android.intent.action.VIEW -d "https://es.wikipedia.org/wiki/Aurora_polar" $PKG
sleep 15; adb exec-out screencap -p > $OUT/2-pagina.png
adb shell input swipe 540 1500 540 600 300; sleep 2; adb exec-out screencap -p > $OUT/3-desplazado.png
# Pestaña personalizada (como la abre Gmail o WhatsApp)
adb shell am start -W -a android.intent.action.VIEW -d "https://example.com" --es android.support.customtabs.extra.SESSION x $PKG
sleep 10; adb exec-out screencap -p > $OUT/5-pestana-personalizada.png
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell monkey -p $PKG --throttle 250 --pct-syskeys 0 --pct-appswitch 0 -s 42 400 > $OUT/monkey.txt 2>&1 || true
sleep 3; adb exec-out screencap -p > $OUT/4-tras-monkey.png
adb logcat -d > $OUT/logcat.txt
if grep -E "FATAL EXCEPTION|ANR in $PKG" $OUT/logcat.txt | head -5 | grep -q .; then
  echo "::error::Fyx se cerró o se congeló en el emulador (mira emulador/logcat.txt)"
  grep -A20 "FATAL EXCEPTION" $OUT/logcat.txt | head -40
  exit 1
fi
echo "Sin cierres ni congelamientos."
