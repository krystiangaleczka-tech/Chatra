#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { readFileSync, mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const defaultSource = resolve(root, 'docs/implementacja_android/Chatra_Pakiet_referencyjny/style_zrodlowe.json');
const defaultOutput = resolve(root, 'build/ch006/screen-fixtures.json');
const expectedCounts = [33, 83, 45, 41, 37, 56, 47, 43, 49, 66];
const scenarioAnchors = [
  ['draft', 'S02', 'Szkic:'],
  ['failed_sms', 'S03', 'Nie udało się wysłać.'],
  ['undo_window', 'S03', 'Wyślę za 3 s'],
  ['hd_message', 'S03', '4 zdjęcia · oryginał'],
  ['hd_original', 'S04', 'Oryginał HD'],
  ['late_schedule', 'S06', 'Wymaga decyzji'],
  ['group_mms_privacy', 'S08', 'Wszyscy zobaczą odpowiedzi i swoje numery telefonów.'],
  ['source_counter_exception', 'S03', '21/160 · 1 SMS'],
];

function sha256(input) {
  return createHash('sha256').update(input).digest('hex');
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

export function buildFixtures(sourceText) {
  const input = JSON.parse(sourceText);
  assert(Array.isArray(input) && input.length === 10, 'Expected exactly ten source screens');
  const screens = input.map((screen, screenIndex) => {
    const id = `S${String(screenIndex + 1).padStart(2, '0')}`;
    assert(screen.screen === id, `Screen order/ID mismatch at ${id}`);
    assert(typeof screen.title === 'string' && screen.title.startsWith('Chatra'), `No title in ${id}`);
    assert(Array.isArray(screen.elements) && screen.elements.length === expectedCounts[screenIndex],
      `Unexpected source element count for ${id}`);
    return {
      id,
      title: screen.title,
      elements: screen.elements.map((element, elementIndex) => {
        assert(typeof element.tag === 'string' && typeof element.text === 'string',
          `Invalid element ${id}-${elementIndex + 1}`);
        return {
          id: `${id}-E${String(elementIndex + 1).padStart(3, '0')}`,
          tag: element.tag,
          text: element.text,
          style: element.style ?? null,
          aria: element.aria ?? null,
        };
      }),
    };
  });
  const scenarios = scenarioAnchors.map(([id, screenId, anchor]) => {
    const screen = screens.find((item) => item.id === screenId);
    const element = screen.elements.find((item) => item.text.includes(anchor));
    assert(element, `Missing source anchor ${id}: ${anchor}`);
    return {
      id,
      screenId,
      elementId: element.id,
      anchor,
      provenance: 'reference_html',
      productionRule: id === 'source_counter_exception'
        ? 'The literal 21/160 is an intentional reference exception, not an SMS counting rule.'
        : null,
    };
  });
  return {
    schemaVersion: 1,
    sourcePath: 'docs/implementacja_android/Chatra_Pakiet_referencyjny/style_zrodlowe.json',
    sourceSha256: sha256(sourceText),
    environment: {
      locale: 'pl-PL',
      timeZone: 'Europe/Warsaw',
      fixedTime: '2026-10-29T15:00:00+01:00',
      viewport: { width: 390, height: 844, unit: 'dp_at_fontScale_1' },
      font: 'Inter',
      simulatedNetwork: 'offline',
      sourceOfContacts: 'reference_fixture_only',
      simulatedSimLabels: ['SIM 1 · Prywatna', 'SIM 2 · Praca'],
    },
    scenarios,
    screens,
  };
}

export function fixtureJson(sourceText) {
  return `${JSON.stringify(buildFixtures(sourceText), null, 2)}\n`;
}

function argsFrom(argv) {
  const args = { source: defaultSource, output: defaultOutput, verify: false };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--verify') args.verify = true;
    else if (argv[i] === '--source' && argv[i + 1]) args.source = resolve(argv[++i]);
    else if (argv[i] === '--output' && argv[i + 1]) args.output = resolve(argv[++i]);
    else throw new Error(`Unknown or incomplete argument: ${argv[i]}`);
  }
  assert(args.source !== args.output, 'Source and output must differ');
  return args;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = argsFrom(process.argv.slice(2));
    const generated = fixtureJson(readFileSync(args.source, 'utf8'));
    if (args.verify) {
      assert(existsSync(args.output), `Fixture does not exist: ${args.output}`);
      assert(readFileSync(args.output, 'utf8') === generated, 'Fixture changed; regenerate from reference');
      console.log('CH006 fixtures: reproducible PASS');
    } else {
      mkdirSync(dirname(args.output), { recursive: true });
      writeFileSync(args.output, generated, 'utf8');
      console.log(`CH006 fixtures: wrote ${JSON.parse(generated).screens.length} screens to ${args.output}`);
    }
  } catch (error) {
    console.error(`CH006 fixture error: ${error.message}`);
    process.exitCode = 1;
  }
}
