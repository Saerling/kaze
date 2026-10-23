// Reemplaza todos los grupos de íconos de un .exe de Windows por los de un .ico (sin Windows).
// Uso: node icono_exe.mjs firefox.exe zev.ico   (necesita: npm i resedit)
import fs from "node:fs";
import * as ResEdit from "resedit";

const [exePath, icoPath] = process.argv.slice(2);
const exe = ResEdit.NtExecutable.from(fs.readFileSync(exePath), { ignoreCert: true });
const res = ResEdit.NtExecutableResource.from(exe);
const ico = ResEdit.Data.IconFile.from(fs.readFileSync(icoPath));
const groups = ResEdit.Resource.IconGroupEntry.fromEntries(res.entries);
for (const g of groups) {
  ResEdit.Resource.IconGroupEntry.replaceIconsForResource(res.entries, g.id, g.lang, ico.icons.map(i => i.data));
}
res.outputResource(exe);
fs.writeFileSync(exePath, Buffer.from(exe.generate()));
console.log(`íconos reemplazados en ${groups.length} grupo(s)`);
