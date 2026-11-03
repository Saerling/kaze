# Licencia de la app Android de Fyx

La app Android de Fyx (todo lo que hay en `android/`) se distribuye en conjunto bajo la
**GNU General Public License v3.0** ([`LICENSE`](LICENSE)). La app de escritorio (`desktop/`) no
cambia: sigue con la Mozilla Public License 2.0 del [`LICENSE`](../LICENSE) raíz.

## Código de terceros

- **Mochy Music** — © realidkroo, GPL-3.0 (<https://github.com/realidkroo/Mochy-Music>).
  Los componentes de diseño y movimiento del paquete `app.kaze.browser.ui.mochy`
  (`android/app/src/main/java/app/kaze/browser/ui/mochy/`) están copiados o adaptados de Mochy.
  Cada archivo dice al inicio de qué archivo de Mochy viene y qué se cambió. Gracias a realidkroo.
  Mochy no trae un aviso de copyright propio (su LICENSE es el texto de la GPL-3.0 sin modificar);
  según su README es un fork de [ViviMusic](https://github.com/vivizzz007/vivi-music) (vivizzz007), que parte de
  [Metrolist](https://github.com/mostafaalagamy/Metrolist) (Mostafa Alagamy), ambos GPL-3.0, así que parte de ese
  código puede ser también de sus autores.
- **Z-Sync** — © 2026 Kjell Behrends, licencia MIT (<https://github.com/Kjell6/Z-Sync>).
  El paquete `app.kaze.browser.zsync` (`android/app/src/main/java/app/kaze/browser/zsync/`) está
  adaptado de Z-Sync; la licencia MIT es compatible con la GPL-3.0 y su aviso se conserva en cada archivo.
- **GeckoView** (Mozilla, MPL-2.0) se usa como biblioteca.

## El resto del código

El código propio de Fyx conserva su aviso MPL-2.0 en cada archivo. La MPL-2.0 permite combinarlo con
código GPL (la GPL-3.0 es una "Secondary License" de la MPL-2.0, sección 3.3), así que la app
compilada se distribuye bajo GPL-3.0 y esos archivos, por separado, también siguen disponibles bajo MPL-2.0.
