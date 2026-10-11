import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

function getGitHubToken() {
  const creds = execSync('echo protocol=https`nhost=github.com | git credential fill', {
    shell: 'powershell.exe',
    encoding: 'utf-8'
  });
  const match = creds.match(/password=(.+)/);
  if (!match) throw new Error('Could not retrieve token from git credential manager');
  return match[1].trim();
}

async function run() {
  const token = getGitHubToken();
  const repo = 'Vijendra-Pratap/JalMargWeb';
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Accept': 'application/vnd.github+json',
    'User-Agent': 'JalMarg-CI-Inspector'
  };

  console.log('Fetching GitHub Actions runs for', repo);
  const runsRes = await fetch(`https://api.github.com/repos/${repo}/actions/runs`, { headers });
  if (!runsRes.ok) {
    console.error('Failed to fetch runs:', runsRes.status, await runsRes.text());
    return;
  }
  const runsData = await runsRes.json();
  const latestRuns = runsData.workflow_runs || [];
  if (latestRuns.length === 0) {
    console.log('No workflow runs found.');
    return;
  }

  const run = latestRuns[0];
  console.log(`\nLatest Run ID: ${run.id}`);
  console.log(`Workflow:     ${run.name}`);
  console.log(`Commit:       ${run.head_sha} ("${run.head_commit?.message}")`);
  console.log(`Event:        ${run.event}`);
  console.log(`Status:       ${run.status}`);
  console.log(`Conclusion:   ${run.conclusion}`);
  console.log(`HTML URL:     ${run.html_url}`);

  // Fetch jobs
  const jobsRes = await fetch(`https://api.github.com/repos/${repo}/actions/runs/${run.id}/jobs`, { headers });
  const jobsData = await jobsRes.json();
  const jobs = jobsData.jobs || [];

  fs.mkdirSync('target/ci_logs', { recursive: true });

  for (const job of jobs) {
    console.log(`\n================================================================`);
    console.log(`JOB: ${job.name} [ID: ${job.id}] -> Status: ${job.status}, Conclusion: ${job.conclusion}`);
    console.log(`================================================================`);

    // Fetch job log
    const logRes = await fetch(`https://api.github.com/repos/${repo}/actions/jobs/${job.id}/logs`, { headers });
    if (logRes.ok) {
      const logText = await logRes.text();
      const logPath = path.join('target/ci_logs', `${job.name.replace(/[^a-zA-Z0-9_-]/g, '_')}.log`);
      fs.writeFileSync(logPath, logText, 'utf-8');

      // Print error snippets
      const lines = logText.split('\n');
      const errorLines = lines.filter(l => 
        l.includes('##[error]') || 
        l.includes('ERROR') || 
        l.includes('FAILED') || 
        l.includes('Exception') || 
        l.includes('Error:') ||
        l.includes('fatal:') ||
        l.includes('FAIL')
      ).slice(0, 30);

      console.log(`Saved log to ${logPath} (${lines.length} lines)`);
      console.log(`Error highlights:`);
      errorLines.forEach(l => console.log('  ' + l.trim()));
    } else {
      console.log(`Could not fetch logs for job ${job.name}: ${logRes.status}`);
    }
  }
}

run().catch(err => {
  console.error('Inspection failed:', err.message);
});
