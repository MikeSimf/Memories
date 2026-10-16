#!/usr/bin/env node
/**
 * Сверяет JPA-маппинг сущностей с SQL-схемой Flyway.
 * Ловит опечатки в @Column и забытые поля — то, что иначе всплыло бы только при старте
 * приложения на настоящей базе. Запуск из корня репозитория: node scripts/check-schema.mjs
 *
 * Умеет три вещи, без которых не обходится схема после переработки v2:
 *  1) читает миграции по порядку и учитывает DROP TABLE / DROP COLUMN / RENAME COLUMN;
 *  2) собирает колонки листового @Entity вместе с цепочкой @MappedSuperclass
 *     (общие поля объявлены один раз и наследуются всеми четырьмя классами);
 *  3) не считает @MappedSuperclass самостоятельной сущностью.
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(new URL('.', import.meta.url).pathname, '..');
const MIGRATIONS = path.join(ROOT, 'server/src/main/resources/db/migration');
const DOMAIN = path.join(ROOT, 'server/src/main/java/ru/dndmemory/domain');

// ------------------------------------------------------------------ SQL

const tables = {};

const parseColumns = (body) => {
  const columns = new Set();
  for (const rawLine of body.split('\n')) {
    const line = rawLine.trim();
    if (!line || /^(CONSTRAINT|PRIMARY|UNIQUE|FOREIGN|CHECK)\b/i.test(line)) continue;
    const column = line.split(/\s+/)[0].replace(/,$/, '');
    if (column) columns.add(column);
  }
  return columns;
};

/** Применяет операторы миграции в том порядке, в каком они записаны в файле. */
const applyMigrations = (text) => {
  const statements = [];
  const collect = (pattern, handler) => {
    for (const match of text.matchAll(pattern)) {
      statements.push({ index: match.index, apply: () => handler(match) });
    }
  };

  collect(/CREATE TABLE (\w+)\s*\(([\s\S]*?)\n\);/g, (m) => {
    tables[m[1]] = parseColumns(m[2]);
  });
  collect(/DROP TABLE\s+(?:IF EXISTS\s+)?(\w+)/gi, (m) => {
    delete tables[m[1]];
  });
  collect(/ALTER TABLE\s+(\w+)\s+ADD COLUMN\s+(?:IF NOT EXISTS\s+)?(\w+)/gi, (m) => {
    if (!tables[m[1]]) tables[m[1]] = new Set();
    tables[m[1]].add(m[2]);
  });
  collect(/ALTER TABLE\s+(\w+)\s+DROP COLUMN\s+(?:IF EXISTS\s+)?(\w+)/gi, (m) => {
    if (tables[m[1]]) tables[m[1]].delete(m[2]);
  });
  collect(/ALTER TABLE\s+(\w+)\s+RENAME COLUMN\s+(\w+)\s+TO\s+(\w+)/gi, (m) => {
    if (tables[m[1]] && tables[m[1]].has(m[2])) {
      tables[m[1]].delete(m[2]);
      tables[m[1]].add(m[3]);
    }
  });

  statements.sort((a, b) => a.index - b.index).forEach((statement) => statement.apply());
};

for (const file of fs.readdirSync(MIGRATIONS).sort()) {
  applyMigrations(fs.readFileSync(path.join(MIGRATIONS, file), 'utf8'));
}

// --------------------------------------------------------------- Java

const camelToSnake = (value) => value.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
const fieldRe = /(?:@Column\(([^)]*)\)\s*)?private\s+(?:final\s+)?[\w<>.\[\]]+\s+(\w+)\s*(?:=|;)/g;

/** Поля файла: имя поля и колонка, в которую оно маппится. */
const parseFields = (source) => {
  const fields = [];
  for (const field of source.matchAll(fieldRe)) {
    const annotation = field[1] || '';
    const nameMatch = /name\s*=\s*"([^"]+)"/.exec(annotation);
    fields.push({ field: field[2], column: nameMatch ? nameMatch[1] : camelToSnake(field[2]) });
  }
  return fields;
};

const classes = {};
for (const file of fs.readdirSync(DOMAIN).filter((f) => f.endsWith('.java'))) {
  const source = fs.readFileSync(path.join(DOMAIN, file), 'utf8');
  const classMatch = /(?:public\s+)?(?:abstract\s+)?class\s+(\w+)(?:\s+extends\s+([\w.]+))?/.exec(source);
  if (!classMatch) continue;

  const tableMatch = /@Table\(\s*name\s*=\s*"([^"]+)"/.exec(source);
  classes[classMatch[1]] = {
    file,
    fields: parseFields(source),
    parent: classMatch[2] ? classMatch[2].split('.').pop() : null,
    entity: /^\s*@Entity\s*$/m.test(source),
    mappedSuperclass: /^\s*@MappedSuperclass\s*$/m.test(source),
    table: tableMatch ? tableMatch[1] : null,
  };
}

/** Поля сущности вместе с полями всей цепочки @MappedSuperclass. */
const fieldsWithInherited = (className, seen = new Set()) => {
  const definition = classes[className];
  if (!definition || seen.has(className)) return [];
  seen.add(className);
  const inherited = definition.parent ? fieldsWithInherited(definition.parent, seen) : [];
  return [...definition.fields, ...inherited];
};

const problems = [];

for (const [className, definition] of Object.entries(classes)) {
  if (!definition.entity) continue;
  if (!definition.table) {
    problems.push(`${definition.file}: нет @Table(name=...)`);
    continue;
  }
  const columns = tables[definition.table];
  if (!columns) {
    problems.push(`${definition.file}: таблицы ${definition.table} нет в миграциях`);
    continue;
  }

  const mapped = new Set();
  for (const { field, column } of fieldsWithInherited(className)) {
    mapped.add(column);
    if (!columns.has(column)) {
      problems.push(`${definition.file}.${field}: колонки ${column} нет в таблице ${definition.table}`);
    }
  }
  for (const column of columns) {
    if (!mapped.has(column)) {
      problems.push(`${definition.table}.${column}: колонка есть в SQL, но не замаплена ни одним полем `
        + `(${definition.file})`);
    }
  }
}

if (problems.length === 0) {
  console.log(`Схема согласована: ${Object.keys(tables).length} таблиц, все колонки замаплены.`);
  process.exit(0);
}
console.error('Найдены расхождения:\n' + problems.map((p) => '  - ' + p).join('\n'));
process.exit(1);
