# GeckoView trae sus propias reglas; estas cubren lo que se carga por reflexión o JNI.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.**
# Receptor del instalador (lo crea Android por nombre).
-keep class app.kaze.browser.engine.UpdateReceiver
