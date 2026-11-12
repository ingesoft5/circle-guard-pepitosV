#!/usr/bin/env node
/**
 * Resolve the next CircleGuard release version using the same rules as
 * jenkins/vars/semver.groovy and calculateVersion.groovy.
 *
 * Usage: node .github/scripts/resolve-release-version.mjs <commit-sha>
 */
import { execFileSync } from 'node:child_process';

function git(args) {
  return execFileSync('git', args, { encoding: 'utf8' }).trim();
}

function parseVersion(value) {
  const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(value ?? '');
  return match ? match.slice(1).map(Number) : null;
}

function compareVersions(left, right) {
  const a = parseVersion(left);
  const b = parseVersion(right);
  if (!a || !b) return 0;
  for (let index = 0; index < 3; index += 1) {
    if (a[index] !== b[index]) return a[index] - b[index];
  }
  return 0;
}

function releaseType(messages) {
  let level = 0;
  for (const message of messages) {
    const text = message.trim();
    if (!text) continue;
    const header = text.split(/\r?\n/, 1)[0].trim();
    const match = /^([a-zA-Z]+)(\([^)]*\))?(!)?:\s+\S/.exec(header);
    if (!match) continue;
    const type = match[1].toLowerCase();
    const breaking = Boolean(match[3]) || /^BREAKING[ -]CHANGE:/m.test(text);
    if (breaking) level = Math.max(level, 3);
    else if (type === 'feat') level = Math.max(level, 2);
    else if (type === 'fix' || type === 'perf') level = Math.max(level, 1);
  }
  return [null, 'patch', 'minor', 'major'][level];
}

function bump(version, type) {
  const [major, minor, patch] = parseVersion(version);
  if (type === 'major') return `${major + 1}.0.0`;
  if (type === 'minor') return `${major}.${minor + 1}.0`;
  return `${major}.${minor}.${patch + 1}`;
}

const sha = process.argv[2];
if (!sha) {
  console.error('Usage: resolve-release-version.mjs <commit-sha>');
  process.exit(2);
}

const tags = git(['tag', '--merged', sha, '--list', 'v[0-9]*'])
  .split(/\s+/)
  .map((tag) => tag.replace(/^v/, ''))
  .filter((tag) => parseVersion(tag))
  .sort(compareVersions);

const previous = tags.at(-1);
if (!previous) {
  // Matches calculateVersion.groovy: the first release is 1.0.0.
  process.stdout.write('1.0.0\n');
  process.exit(0);
}

const log = git(['log', '--no-merges', '--format=%B%x1e', `v${previous}..${sha}`]);
const messages = log.split('\u001e').map((message) => message.trim()).filter(Boolean);
const type = releaseType(messages);
if (!type) {
  console.error(`No releasable Conventional Commits found since v${previous}; no release branch will be created.`);
  process.exit(1);
}

process.stdout.write(`${bump(previous, type)}\n`);
