#!/usr/bin/env node
/**
 * Проверка согласованности Android-проекта без компилятора.
 *
 * Сверяет ссылки Java -> ресурсы (R.id, R.string, R.layout, R.drawable, R.menu, ...),
 * ссылки в XML (@string/..., @drawable/..., @style/...), наличие активностей из
 * манифеста и совпадение идентификаторов макета с тем, что ищет код.
 *
 * Запуск из корня репозитория: node scripts/check-android.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(new URL('.', import.meta.url).pathname, '..');
const APP = path.join(ROOT, 'android/app/src/main');
const RES = path.join(APP, 'res');
const JAVA = path.join(APP, 'java');

const read = (file) => fs.readFileSync(file, 'utf8');
const walk = (dir, filter = () => true) => {
  if (!fs.existsSync(dir)) return [];
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full, filter));
    else if (filter(full)) out.push(full);
  }
  return out;
};

// Ресурсы, которые объявляют подключённые библиотеки, а не наш проект
const LIBRARY_STRINGS = new Set([
  'appbar_scrolling_view_behavior',
  'bottom_sheet_behavior',
  'password_toggle_content_description',
  'search_menu_title',
]);

const javaFiles = walk(JAVA, (f) => f.endsWith('.java'));
const xmlFiles = walk(APP, (f) => f.endsWith('.xml'));
if (javaFiles.length === 0) {
  console.error('Не найден исходный код Android в ' + JAVA);
  process.exit(1);
}

// ---- собираем доступные ресурсы ------------------------------------------

const ids = new Set();
const values = {}; // тип -> Set имён
const fileResources = {}; // тип -> Set имён (layout, drawable, menu, xml, mipmap, anim)

for (const file of xmlFiles) {
  const source = read(file);
  for (const match of source.matchAll(/@\+?id\/([A-Za-z_][\w]*)/g)) ids.add(match[1]);
  for (const match of source.matchAll(/<item[^>]*type="id"[^>]*name="([\w]+)"/g)) ids.add(match[1]);
  for (const match of source.matchAll(/<(string|color|dimen|style|bool|integer|array|plurals|attr)[^>]*name="([\w.]+)"/g)) {
    (values[match[1]] ||= new Set()).add(match[2]);
  }
}

for (const file of walk(RES)) {
  const rel = path.relative(RES, file).split(path.sep);
  const folder = rel[0];
  const base = path.basename(file, path.extname(file));
  const type = folder.split('-')[0];
  if (['layout', 'drawable', 'menu', 'xml', 'mipmap', 'anim', 'raw', 'color', 'navigation', 'font'].includes(type)) {
    (fileResources[type] ||= new Set()).add(base);
  }
}

// ---- каталог классов: какие типы вообще существуют ------------------------

const classIndex = new Map(); // полное имя -> файл
const packageOf = new Map();  // файл -> пакет
for (const file of javaFiles) {
  const source = read(file);
  const pkg = /^\s*package\s+([\w.]+)\s*;/m.exec(source);
  const short = path.relative(ROOT, file);
  if (!pkg) {
    problems.push(short + ": нет объявления package");
    continue;
  }
  packageOf.set(file, pkg[1]);
  const expectedDir = path.join(JAVA, ...pkg[1].split("."));
  if (path.dirname(file) !== expectedDir) {
    problems.push(short + ": пакет " + pkg[1] + " не совпадает с каталогом");
  }
  for (const decl of source.matchAll(/\b(?:public\s+|final\s+|abstract\s+)*(?:class|interface|enum)\s+([A-Z]\w*)/g)) {
    classIndex.set(pkg[1] + "." + decl[1], file);
  }
}

// ---- проверяем ссылки из Java --------------------------------------------

const problems = [];
const R_TYPES = {
  id: (name) => ids.has(name),
  string: (name) => values.string?.has(name),
  layout: (name) => fileResources.layout?.has(name),
  drawable: (name) => fileResources.drawable?.has(name),
  menu: (name) => fileResources.menu?.has(name),
  mipmap: (name) => fileResources.mipmap?.has(name),
  xml: (name) => fileResources.xml?.has(name),
  color: (name) => values.color?.has(name) || fileResources.color?.has(name),
  dimen: (name) => values.dimen?.has(name),
  style: (name) => values.style?.has(name),
  bool: (name) => values.bool?.has(name),
  integer: (name) => values.integer?.has(name),
  array: (name) => values.array?.has(name),
  anim: (name) => fileResources.anim?.has(name),
};

for (const file of javaFiles) {
  const source = read(file);
  const short = path.relative(ROOT, file);
  for (const match of source.matchAll(/(?<![\w.])R\.(\w+)\.(\w+)/g)) {
    const [, type, name] = match;
    const check = R_TYPES[type];
    if (!check) continue;
    if (!check(name)) problems.push(`${short}: R.${type}.${name} — ресурс не найден`);
  }
  // импорты наших классов должны указывать на существующие файлы
  for (const match of source.matchAll(/^\s*import\s+(ru\.dndmemory[\w.]*)\s*;/gm)) {
    const imported = match[1];
    // R и BuildConfig генерирует Gradle — в исходниках их нет
    // R, BuildConfig и классы ViewBinding генерирует Gradle — в исходниках их нет
    if (/\.(R|BuildConfig)$/.test(imported) || imported.includes(".databinding")) continue;
    if (!classIndex.has(imported) && !classIndex.has(imported.replace(/\.\w+$/, ""))) {
      problems.push(short + ": import " + imported + " — такого класса нет");
    }
  }

  // switch/case по идентификаторам вида case R.id.foo
  for (const match of source.matchAll(/case\s+(?<![\w.])R\.id\.(\w+)/g)) {
    if (!ids.has(match[1])) problems.push(`${short}: case R.id.${match[1]} — идентификатор не найден`);
  }
}

// ---- проверяем ссылки из XML ---------------------------------------------

for (const file of xmlFiles) {
  const source = read(file);
  const short = path.relative(ROOT, file);
  for (const match of source.matchAll(/["@]@(string|drawable|color|dimen|style|layout|menu|mipmap|anim|xml|array|bool|integer)\/([\w.]+)/g)) {
    const [, type, name] = match;
    if (type === 'drawable' && (fileResources.drawable?.has(name) || fileResources.mipmap?.has(name))) continue;
    if (['style'].includes(type)) {
      if (values.style?.has(name)) continue;
      // стили платформы вида @style/Theme.MaterialComponents... не проверяем
      if (name.includes('.') && !values.style?.has(name)) continue;
      problems.push(`${short}: @style/${name} — стиль не найден`);
      continue;
    }
    if (type === 'string' && (name.startsWith('android') || LIBRARY_STRINGS.has(name))) continue;
    if (R_TYPES[type] && !R_TYPES[type](name)) {
      problems.push(`${short}: @${type}/${name} — ресурс не найден`);
    }
  }
}

// ---- манифест: активности, сервисы, разрешения ---------------------------

const manifestPath = path.join(APP, 'AndroidManifest.xml');
if (!fs.existsSync(manifestPath)) {
  problems.push('AndroidManifest.xml отсутствует');
} else {
  const manifest = read(manifestPath);
  const javaSource = javaFiles.map(read).join('\n');
  for (const match of manifest.matchAll(/android:name="\.?([\w.]+)"/g)) {
    const name = match[1];
    // пропускаем провайдеры, разрешения и системные классы
    if (/^(android|androidx|com\.google|com\.journeyapps|java|kotlin)\./.test(name)) continue;
    const simple = name.split('.').pop();
    if (!javaSource.includes(`class ${simple}`)) {
      problems.push(`AndroidManifest.xml: класс ${name} не найден в исходниках`);
    }
  }
  if (!manifest.includes('android.permission.INTERNET')) {
    problems.push('AndroidManifest.xml: нет разрешения INTERNET — сеть работать не будет');
  }
  if (!manifest.includes('android.permission.CAMERA')) {
    problems.push('AndroidManifest.xml: нет разрешения CAMERA — сканирование QR не заработает');
  }
  if (!manifest.includes('dndmemory://join')) {
    problems.push('AndroidManifest.xml: нет deep link dndmemory://join для приглашений');
  }
}

// ---- итог ----------------------------------------------------------------

const stats = [
  `Java-файлов: ${javaFiles.length}`,
  `XML-файлов: ${xmlFiles.length}`,
  `идентификаторов: ${ids.size}`,
  `строк: ${values.string?.size ?? 0}`,
  `макетов: ${fileResources.layout?.size ?? 0}`,
  `drawable: ${fileResources.drawable?.size ?? 0}`,
];

if (problems.length === 0) {
  console.log('Android-проект согласован. ' + stats.join(', '));
  process.exit(0);
}
console.error(`Найдено расхождений: ${problems.length}\n` + problems.slice(0, 80).map((p) => '  - ' + p).join('\n'));
if (problems.length > 80) console.error(`  … и ещё ${problems.length - 80}`);
process.exit(1);

