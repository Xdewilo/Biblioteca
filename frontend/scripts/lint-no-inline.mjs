#!/usr/bin/env node
// Prohibe plantillas y estilos en línea en componentes Angular; exit 1 si hay violaciones fuera de la allowlist.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const ROOT = 'src/app';

// Allowlist temporal: { file: sufijo de ruta, until: fecha ISO (vencida cuenta como violación) }.
const ALLOWLIST = [];

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) yield* walk(full);
    else yield full;
  }
}

function normalize(p) {
  return p.split(sep).join('/');
}

const violations = [];
for (const file of walk(ROOT)) {
  if (!file.endsWith('.ts') || file.endsWith('.spec.ts') || file.endsWith('.d.ts')) continue;
  const rel = normalize(relative('.', file));
  const src = readFileSync(file, 'utf8');
  const hasInlineTemplate = /^[ \t]*template\s*:\s*(`|')/m.test(src);
  const hasInlineStyles = /^[ \t]*styles\s*:\s*[\[`]/m.test(src);
  if (!hasInlineTemplate && !hasInlineStyles) continue;

  const allowEntry = ALLOWLIST.find((a) => rel.endsWith(a.file));
  if (allowEntry) {
    if (new Date(allowEntry.until) < new Date()) {
      violations.push(`${rel} — allowlist VENCIDA (${allowEntry.until})`);
    }
    continue;
  }
  violations.push(rel);
}

if (violations.length) {
  console.error(`\nlint:no-inline — ${violations.length} componente(s) con template/styles inline:\n`);
  for (const v of violations) console.error(`  ✗ ${v}`);
  console.error('\nExtrae con: node scripts/extract-inline-templates.mjs <archivo.ts>');
  process.exit(1);
}
console.log('lint:no-inline OK — sin templates/estilos inline en src/app.');
