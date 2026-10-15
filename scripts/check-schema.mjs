#!/usr/bin/env node
/**
 * Сверяет JPA-маппинг сущностей с SQL-схемой Flyway.
 * Ловит опечатки в @Column и забытые поля — то, что иначе всплыло бы только при старте
 * приложения на настоящей базе. Запуск из корня репозитория: node scripts/check-schema.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(new URL('.', import.meta.url).pathname, '..');
const MIGRATIONS = path.join(ROOT, 'server/src/main/resources/db/migration');
const DOMAIN = path.join(ROOT, 'server/src/main/java/ru/dndmemory/domain');

const sql = fs.readdirSync(MIGRATIONS).sort()
  .map((f) => fs.readFileSync(path.join(MIGRATIONS, f), 'utf8')).join('\n');

const tables = {};
for (const match of sql.matchAll(/CREATE TABLE (\w+)\s*\(([\s\S]*?)\n\);/g)) {
  const columns = new Set();
  for (const rawLine of match[2].split('\n')) {
    const line = rawLine.trim();
    if (!line || /^(CONSTRAINT|PRIMARY|UNIQUE|FOREIGN|CHECK)\b/i.test(line)) continue;
    const column = line.split(/\s+/)[0].replace(/,$/, '');
    if (column) columns.add(column);
  }
  tables[match[1]] = columns;
}

const camelToSnake = (value) => value.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
const problems = [];

for (const file of fs.readdirSync(DOMAIN).filter((f) => f.endsWith('.java'))) {
  const source = fs.readFileSync(path.join(DOMAIN, file), 'utf8');
  if (!source.includes('@Entity')) continue;

  const tableMatch = /@Table\(\s*name\s*=\s*"([^"]+)"/.exec(source);
  if (!tableMatch) {
    problems.push(`${file}: нет @Table(name=...)`);
    continue;
  }
  const table = tableMatch[1];
  const columns = tables[table];
  if (!columns) {
    problems.push(`${file}: таблицы ${table} нет в миграциях`);
    continue;
  }

  const mapped = new Set();
  const fieldRe = /(?:@Column\(([^)]*)\)\s*)?private\s+(?:final\s+)?[\w<>.\[\]]+\s+(\w+)\s*(?:=|;)/g;
  for (const field of source.matchAll(fieldRe)) {
    const annotation = field[1] || '';
    const nameMatch = /name\s*=\s*"([^"]+)"/.exec(annotation);
    const column = nameMatch ? nameMatch[1] : camelToSnake(field[2]);
    mapped.add(column);
    if (!columns.has(column)) {
      problems.push(`${file}.${field[2]}: колонки ${column} нет в таблице ${table}`);
    }
  }
  for (const column of columns) {
    if (!mapped.has(column)) {
      problems.push(`${table}.${column}: колонка есть в SQL, но не замаплена ни одним полем (${file})`);
    }
  }
}

if (problems.length === 0) {
  console.log(`Схема согласована: ${Object.keys(tables).length} таблиц, все колонки замаплены.`);
  process.exit(0);
}
console.error('Найдены расхождения:\n' + problems.map((p) => '  - ' + p).join('\n'));
process.exit(1);
