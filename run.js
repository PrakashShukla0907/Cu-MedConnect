const { spawn } = require('child_process');

const env = { ...process.env };

const isWin = process.platform === 'win32';
const mvnCmd = isWin ? 'mvn.cmd' : 'mvn';

const commandArg = process.argv[2] || 'run';
let mvnArgs = ['spring-boot:run'];

if (commandArg === 'build') {
  mvnArgs = ['clean', 'package', '-DskipTests'];
} else if (commandArg === 'test') {
  mvnArgs = ['test'];
} else if (process.argv.length > 2 && commandArg !== 'run' && commandArg !== 'dev') {
  mvnArgs = process.argv.slice(2);
}

console.log(`> Running Maven: ${mvnCmd} ${mvnArgs.join(' ')}\n`);

const child = spawn(mvnCmd, mvnArgs, {
  stdio: 'inherit',
  shell: true,
  env
});

child.on('exit', (code) => {
  process.exit(code ?? 0);
});

child.on('error', (error) => {
  console.error(`Could not start Maven: ${error.message}`);
  process.exit(1);
});
