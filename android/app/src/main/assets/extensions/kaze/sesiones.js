/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Mantener las sesiones iniciadas: muchas páginas guardan el inicio de sesión en una cookie "de sesión"
// (sin fecha), que se borra al cerrar la app. Firefox de escritorio las recupera al reabrir; en Android
// no, así que aquí se les da una duración de 30 días (como "Recordarme").
"use strict";
const DAYS = 30;
browser.cookies.onChanged.addListener(({ removed, cookie: c }) => {
  if (removed || !c.session) return;
  const d = {
    url: (c.secure ? "https://" : "http://") + c.domain.replace(/^\./, "") + c.path,
    name: c.name, value: c.value, path: c.path, secure: c.secure, httpOnly: c.httpOnly, sameSite: c.sameSite,
    storeId: c.storeId, firstPartyDomain: c.firstPartyDomain, expirationDate: Math.floor(Date.now() / 1000) + DAYS * 86400,
  };
  if (c.partitionKey) d.partitionKey = c.partitionKey;
  if (!c.hostOnly) d.domain = c.domain;
  browser.cookies.set(d).catch(() => {});
});
