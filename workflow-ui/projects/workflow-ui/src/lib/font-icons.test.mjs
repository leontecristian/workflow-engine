import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, join } from 'node:path';
import { createHash } from 'node:crypto';

const repo = fileURLToPath(new URL('../../../../../', import.meta.url));
const library = resolve(repo, 'workflow-ui/projects/workflow-ui');
const notice = readFileSync(join(library, 'material-symbols.NOTICE.txt'), 'utf8');
const names = new Set(notice.match(/^icon_names=(.+)$/m)[1].trim().split(','));
function sources(directory) {
  return readdirSync(directory, {withFileTypes:true}).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? sources(path) : /\.(ts|html)$/.test(path) ? [path] : [];
  });
}

test('direct and metadata-driven icon names are present in the bundled font subset', () => {
  let directIcons = 0, dynamicIcons = 0;
  for (const path of [...sources(join(library, 'src')), ...sources(join(library, 'administration')),
    ...sources(join(library, 'runtime')), ...sources(resolve(repo, 'workflow-test-host/frontend/src'))]) {
    const text = readFileSync(path, 'utf8');
    assert.ok(!/console-icon|ConsoleIcon|svgIcon=/.test(text), path);
    for (const icon of text.matchAll(/<mat-icon\b[^>]*>([^<]+)<\/mat-icon>/g)) {
      if (icon[1].includes('{{')) { dynamicIcons++; continue; }
      directIcons++;
      assert.ok(names.has(icon[1].trim()), path + ': ' + icon[1]);
    }
    for (const name of text.matchAll(/\bicon\s*:\s*["'](\w+)["']|\bicon="(\w+)"|\bicon\s*=\s*input\(["'](\w+)["']\)/g)) {
      assert.ok(names.has(name[1] ?? name[2] ?? name[3]), path + ': ' + name[0]);
    }
  }
  assert.ok(directIcons > 40);
  assert.ok(dynamicIcons >= 4);
});

test('local fixed-axis font and opt-in CSS are packaged and configured in both consumers', () => {
  const font = readFileSync(join(library, 'fonts/material-symbols-outlined.ttf'));
  assert.equal(font.readUInt32BE(0), 0x00010000);
  assert.equal(createHash('sha256').update(font).digest('hex'), notice.match(/SHA256: (\w+)/)[1]);
  const tags = Array.from({length:font.readUInt16BE(4)}, (_,i) => font.toString('ascii', 12+i*16, 16+i*16));
  assert.ok(tags.includes('GSUB'));
  assert.ok(!tags.includes('fvar'), 'Font request must retain the selected fixed axes');
  const css = readFileSync(join(library, 'styles/material-symbols.css'), 'utf8');
  assert.match(css, /url\('\.\.\/fonts\/material-symbols-outlined\.ttf'\)/);
  assert.ok(!/https?:\/\//.test(css));
  const packaging = JSON.parse(readFileSync(join(library, 'ng-package.json'), 'utf8'));
  for (const asset of ['fonts/material-symbols-outlined.ttf', 'styles/material-symbols.css', 'material-symbols.LICENSE.txt', 'material-symbols.NOTICE.txt']) {
    assert.ok(packaging.assets.includes(asset));
  }
  for (const consumer of ['workflow-test-host/frontend', 'workflow-ui/verification/ngmodule-host']) {
    const config = JSON.parse(readFileSync(resolve(repo, consumer, 'angular.json'), 'utf8'));
    const project = Object.values(config.projects)[0];
    assert.ok(project.architect.build.options.styles.includes('node_modules/@workflow-engine/ui/styles/material-symbols.css'));
    const bootstrap = readFileSync(resolve(repo, consumer, 'src/main.ts'), 'utf8');
    assert.match(bootstrap, /setDefaultFontSetClass\('material-symbols-outlined','mat-ligature-font'\)/);
  }
});
