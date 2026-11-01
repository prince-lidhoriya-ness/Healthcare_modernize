// STORY-006 acceptance checks: the frontend on the ADR-001 versions (Node 26.13.0, React 19.3.1,
// react-router 8.5.1, Vite 8.3.5 + @vitejs/plugin-react 6.1.2, TypeScript 6.0.3, axios 1.21.0)
// with the legacy routes, labels, REST paths and session key unchanged.
//
//   AC-a  npm ci, tsc and vite build succeed in node:26.13.0-alpine3.24 with the pinned versions installed
//   AC-b  no react-router-dom import or dependency remains
//   AC-c  the built bundle still contains the legacy routes, portal labels, REST paths and session key
//
// The build is part of the check: run the whole chain from the frontend directory inside the pinned image
// (Git Bash on Windows: prefix MSYS_NO_PATHCONV=1; behind a TLS-inspecting proxy, also mount its CA and set
// NODE_EXTRA_CA_CERTS). Any failing step makes the run fail.
//   docker run --rm -v "<abs path>/frontend:/app" -v /app/node_modules -w /app node:26.13.0-alpine3.24 \
//     sh -c "rm -rf dist && npm ci && npm run build && node acceptance/STORY-006-acceptance.mjs"
import { existsSync, readdirSync, readFileSync } from 'node:fs'

const REMOVED_PACKAGE = 'react-router-dom'

const PINS = {
  dependencies: { axios: '1.21.0', react: '19.3.1', 'react-dom': '19.3.1', 'react-router': '8.5.1' },
  devDependencies: {
    '@types/react': '19.3.0',
    '@types/react-dom': '19.3.0',
    '@vitejs/plugin-react': '6.1.2',
    typescript: '6.0.3',
    vite: '8.3.5',
  },
}

// Route paths of the legacy App.tsx, plus the nav links of both portals
const ROUTES = [
  '/admin/login', '/admin', 'patients', 'patients/:id', 'patients/:id/edit', 'doctors', 'doctors/new',
  'appointments', 'appointments/new', '/customer/login', '/customer/register', '/customer', 'book', '*',
  '/admin/patients', '/admin/doctors', '/admin/appointments', '/customer/book',
]

// Portal names and the labels the Playwright suite clicks
const LABELS = [
  'Admin portal', 'Customer portal', 'Dashboard', 'Patients', 'Doctors', 'Appointments', 'My visits',
  'Book a visit', 'Sign in', 'Sign out', 'Register doctor', 'Book visit', 'Create account',
]

// REST calls keep the /api base and their paths; the session stays under the same localStorage key
const REST_AND_SESSION = [
  '/api', '/auth/login', '/auth/register', '/auth/me', '/auth/logout', '/reference-data', '/patients',
  '/appointments', '/doctors', '/dashboard/summary', '/customer/profile', '/customer/appointments',
  'ivirma.session',
]

const results = []
const check = (name, ok) => {
  results.push(ok)
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}`)
}
const readText = (file) => (existsSync(file) ? readFileSync(file, 'utf8') : '')

console.log('== AC-a  toolchain and build')
check(`AC-a runs on node v26.13.0 (found ${process.version})`, process.version === 'v26.13.0')
const dockerfile = readText('Dockerfile')
const dockerfileLines = dockerfile.split('\n').map((line) => line.trim())
check('AC-a Dockerfile build stage is node:26.13.0-alpine3.24', dockerfileLines.includes('FROM node:26.13.0-alpine3.24 AS build'))
check('AC-a Dockerfile has no node 18 image left', !dockerfile.includes('node:18'))
const pkg = JSON.parse(readText('package.json'))
for (const [section, pins] of Object.entries(PINS)) {
  for (const [name, version] of Object.entries(pins)) {
    check(`AC-a package.json ${section} pins ${name} ${version}`, pkg[section]?.[name] === version)
    const installed = readText(`node_modules/${name}/package.json`)
    check(`AC-a installed ${name} is ${version}`, installed !== '' && JSON.parse(installed).version === version)
  }
}
check('AC-a tsc and vite build produced dist/index.html', existsSync('dist/index.html'))

console.log(`== AC-b  ${REMOVED_PACKAGE} removed`)
const offenders = readdirSync('src', { recursive: true })
  .filter((file) => /\.tsx?$/.test(file))
  .filter((file) => readText(`src/${file}`).includes(REMOVED_PACKAGE))
for (const file of offenders) {
  console.log(`      still imported in src/${file}`)
}
check(`AC-b no ${REMOVED_PACKAGE} import in src (${offenders.length} file(s) found)`, offenders.length === 0)
check(`AC-b ${REMOVED_PACKAGE} not declared in package.json`, !readText('package.json').includes(REMOVED_PACKAGE))
check(`AC-b ${REMOVED_PACKAGE} absent from package-lock.json`, !readText('package-lock.json').includes(REMOVED_PACKAGE))
check(`AC-b ${REMOVED_PACKAGE} not installed`, !existsSync(`node_modules/${REMOVED_PACKAGE}`))

console.log('== AC-c  built bundle (dist/)')
const chunks = existsSync('dist/assets') ? readdirSync('dist/assets').filter((file) => file.endsWith('.js')) : []
const bundle = chunks.map((file) => readText(`dist/assets/${file}`)).join('\n')
const isStringLiteral = (text) => ['"', "'", '`'].some((quote) => bundle.includes(quote + text + quote))
check('AC-c dist/index.html mounts #root', readText('dist/index.html').includes('<div id="root">'))
for (const route of ROUTES) {
  check(`AC-c route ${route}`, isStringLiteral(route))
}
for (const label of LABELS) {
  check(`AC-c label '${label}'`, isStringLiteral(label))
}
for (const literal of REST_AND_SESSION) {
  check(`AC-c REST/session literal ${literal}`, isStringLiteral(literal))
}

const failures = results.filter((ok) => !ok).length
console.log(`== STORY-006 acceptance: ${results.length} checks, ${failures} failing`)
process.exitCode = failures === 0 ? 0 : 1
