import fs from 'node:fs';
import path from 'node:path';

const ROOT_DIR = process.env.PROJECT_ROOT || process.cwd();

// Parse .gitignore rules
const gitignoreContent = fs.readFileSync(path.join(ROOT_DIR, '.gitignore'), 'utf8');
const rawRules = gitignoreContent
  .split('\n')
  .map(l => l.trim())
  .filter(l => l && !l.startsWith('#'));

function parseIgnoreRules(rules) {
  return rules.map(rule => {
    let negated = false;
    if (rule.startsWith('!')) {
      negated = true;
      rule = rule.substring(1);
    }
    const isDirOnly = rule.endsWith('/');
    const cleanPattern = rule.replace(/\/$/, '');
    return {
      negated,
      isDirOnly,
      pattern: cleanPattern,
      raw: rule
    };
  });
}

const parsedRules = parseIgnoreRules(rawRules);

function isIgnored(relPath, isDirectory) {
  const normalized = relPath.replace(/\\/g, '/');
  const pathParts = normalized.split('/');
  let ignored = false;

  for (const rule of parsedRules) {
    let match = false;
    let pattern = rule.pattern;

    if (rule.isDirOnly && !isDirectory && !normalized.includes('/')) {
      // dir-only rule on root file
      continue;
    }

    if (pattern.startsWith('**/')) {
      const sub = pattern.substring(3);
      match = normalized.endsWith('/' + sub) || normalized === sub || normalized.includes('/' + sub + '/');
    } else if (pattern.startsWith('*.')) {
      const ext = pattern.substring(1);
      match = normalized.endsWith(ext);
    } else if (pattern.includes('*')) {
      const regexStr = '^' + pattern.replace(/\./g, '\\.').replace(/\*/g, '.*') + '$';
      const re = new RegExp(regexStr);
      match = re.test(normalized) || pathParts.some(part => re.test(part));
    } else {
      match = normalized === pattern || 
              normalized.startsWith(pattern + '/') || 
              pathParts.includes(pattern);
    }

    if (match) {
      ignored = !rule.negated;
    }
  }

  return ignored;
}

// Traverse and categorize all files
const allFiles = [];
function walk(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    const relPath = path.relative(ROOT_DIR, fullPath).replace(/\\/g, '/');
    const isDir = entry.isDirectory();

    const ignored = isIgnored(relPath, isDir);
    if (isDir) {
      if (ignored) {
        allFiles.push({ relPath, isDir: true, ignored: true, size: 0 });
      } else {
        walk(fullPath);
      }
    } else {
      const stats = fs.statSync(fullPath);
      allFiles.push({ relPath, isDir: false, ignored, size: stats.size });
    }
  }
}

walk(ROOT_DIR);

const trackedCandidates = allFiles.filter(f => !f.ignored && !f.isDir);
const excludedFiles = allFiles.filter(f => f.ignored);

console.log('================================================================');
console.log('JALMARG GITHUB PUBLICATION CANDIDATE AUDIT');
console.log('================================================================');
console.log(`Total items evaluated:   ${allFiles.length}`);
console.log(`Tracked candidate files: ${trackedCandidates.length}`);
console.log(`Excluded items:          ${excludedFiles.length}`);

// Large file detection
console.log('\n--- LARGE FILE SIZE CHECK (>50MB Warning, >100MB Blocker) ---');
let largeFileIssues = 0;
for (const file of trackedCandidates) {
  if (file.size > 100 * 1024 * 1024) {
    console.error(`[BLOCKER: EXCEEDS 100MB] ${file.relPath} (${(file.size / 1024 / 1024).toFixed(2)} MB)`);
    largeFileIssues++;
  } else if (file.size > 50 * 1024 * 1024) {
    console.warn(`[WARNING: EXCEEDS 50MB]  ${file.relPath} (${(file.size / 1024 / 1024).toFixed(2)} MB)`);
  }
}
if (largeFileIssues === 0) {
  console.log('✓ No tracked candidate files exceed GitHub 100MB hard limit.');
}

// Secret Scanner Patterns
const secretPatterns = [
  { name: 'AWS Access Key ID', regex: /AKIA[0-9A-Z]{16}/ },
  { name: 'AWS Secret Key', regex: /(?:aws_secret_access_key|aws_secret_key)\s*[:=]\s*['"][A-Za-z0-9/+=]{40}['"]/i },
  { name: 'Private Key Block', regex: /-----BEGIN (?:RSA|EC|DSA|OPENSSH|PGP) PRIVATE KEY-----/ },
  { name: 'GitHub Token', regex: /gh[pousr]_[A-Za-z0-9_]{36,}/ },
  { name: 'Hardcoded JDBC DB Password', regex: /jdbc:postgresql:\/\/[^:]+:[^@\s]+@/i },
  { name: 'Hardcoded Plaintext Secret String', regex: /(?:api[_-]?key|auth_token|admin_key|secret_key)\s*[:=]\s*['"][A-Za-z0-9!@#$%^&*()_+=-]{16,}['"]/i }
];

console.log('\n--- SECRET & CREDENTIAL SCAN OF CANDIDATES ---');
const findings = [];

for (const file of trackedCandidates) {
  // Only inspect text files
  const ext = path.extname(file.relPath).toLowerCase();
  const textExts = ['.java', '.js', '.jsx', '.mjs', '.ts', '.tsx', '.py', '.sql', '.properties', '.xml', '.json', '.yml', '.yaml', '.md', '.txt', '.html', '.css', '.sh', '.bat', '.ps1'];
  if (!textExts.includes(ext)) continue;

  const fullPath = path.join(ROOT_DIR, file.relPath);
  try {
    const content = fs.readFileSync(fullPath, 'utf8');
    const lines = content.split('\n');
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      // Skip comments or example files where keys are template placeholders
      if (file.relPath.endsWith('.example') || file.relPath.endsWith('.template')) continue;
      
      for (const pat of secretPatterns) {
        if (pat.regex.test(line)) {
          // Verify it's not a dummy placeholder like 'change_this...' or 'your_...'
          if (line.includes('change_this') || line.includes('your_') || line.includes('<') || line.includes('${')) continue;
          findings.push({
            file: file.relPath,
            line: i + 1,
            rule: pat.name,
            matchedSnippet: '[REDACTED_SECRET_CONFIRMED]'
          });
        }
      }
    }
  } catch (err) {
    // Binary or unreadable
  }
}

if (findings.length === 0) {
  console.log('✓ Zero high-risk production secrets found in candidate files.');
} else {
  console.log(`! Detected ${findings.length} potential secret findings:`);
  for (const f of findings) {
    console.log(`  - [${f.rule}] in ${f.file}:${f.line} (Content Redacted)`);
  }
}
