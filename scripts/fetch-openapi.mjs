#!/usr/bin/env node
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const outFile = path.join(root, 'packages', 'api-client', 'openapi.json');
const url = process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs';

async function main() {
  console.log(`Fetching OpenAPI from ${url}`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch OpenAPI: ${response.status} ${response.statusText}`);
  }
  const json = await response.json();
  await mkdir(path.dirname(outFile), { recursive: true });
  await writeFile(outFile, `${JSON.stringify(json, null, 2)}\n`, 'utf8');
  console.log(`Wrote ${outFile}`);
}

main().catch((err) => {
  console.error(err instanceof Error ? err.message : err);
  process.exit(1);
});
