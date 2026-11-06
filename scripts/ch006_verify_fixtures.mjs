#!/usr/bin/env node
import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildFixtures, fixtureJson } from './ch006_build_fixtures.mjs';

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = resolve(repo, 'docs/implementacja_android/Chatra_Pakiet_referencyjny/style_zrodlowe.json');
const sourceText = readFileSync(source, 'utf8');
const fixtures = buildFixtures(sourceText);
const duplicate = fixtureJson(sourceText);
assert.equal(duplicate, fixtureJson(sourceText), 'Fixture serialization must be deterministic');
assert.equal(fixtures.screens.length, 10);
assert.equal(fixtures.scenarios.length, 8);
assert.equal(new Set(fixtures.screens.flatMap((screen) => screen.elements.map((e) => e.id))).size,
  fixtures.screens.reduce((n, screen) => n + screen.elements.length, 0), 'Element IDs must be unique');
for (const scenario of fixtures.scenarios) {
  const screen = fixtures.screens.find((s) => s.id === scenario.screenId);
  assert.ok(screen.elements.some((e) => e.id === scenario.elementId && e.text.includes(scenario.anchor)));
}
assert.ok(fixtures.screens.some((s) => s.elements.some((e) => e.text.includes('ą'))),
  'Expected Polish-language fixture');
assert.ok(fixtures.scenarios.find((s) => s.id === 'source_counter_exception').productionRule.includes('not an SMS'));
assert.deepEqual(fixtures.environment.simulatedSimLabels, ['SIM 1 · Prywatna', 'SIM 2 · Praca']);
assert.equal(fixtures.environment.simulatedNetwork, 'offline');
assert.equal(fixtures.environment.fixedTime, '2026-10-29T15:00:00+01:00');
assert.equal(fixtures.screens[9].id, 'S10');
assert.equal(fixtures.screens[0].elements[0].id, 'S01-E001');
console.log(`CH006 fixture source verification PASS: ${fixtures.screens.length} screens, ` +
  `${fixtures.screens.reduce((n, s) => n + s.elements.length, 0)} elements, ` +
  `${fixtures.scenarios.length} scenario anchors, deterministic serialization`);
