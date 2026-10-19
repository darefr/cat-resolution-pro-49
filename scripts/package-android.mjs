import { mkdir, readFile, writeFile, copyFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const output = path.join(root, 'public/downloads')
const apk = path.join(root, 'android/app/build/outputs/apk/debug/app-debug.apk')
const buildConfig = await readFile(path.join(root, 'android/app/build.gradle'), 'utf8')
const version = buildConfig.match(/versionName\s+'([\d.]+)'/)?.[1]
if (!version) throw new Error('Android versionName is missing.')
const versionedName = `cat-resolution-${version}.apk`
await mkdir(output, { recursive: true })
await Promise.all([
  copyFile(apk, path.join(output, 'cat-resolution.apk')),
  copyFile(apk, path.join(output, versionedName)),
])
const checksum = createHash('sha256').update(await readFile(apk)).digest('hex')
await Promise.all([
  writeFile(path.join(output, 'cat-resolution.apk.sha256'), `${checksum}  cat-resolution.apk\n`),
  writeFile(path.join(output, `${versionedName}.sha256`), `${checksum}  ${versionedName}\n`),
])

execFileSync('python3', ['-c', `
import pathlib, zipfile
root = pathlib.Path(${JSON.stringify(root)})
out = root / 'public/downloads/cat-resolution-source.zip'
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
    for p in (root / 'android').rglob('*'):
        relative = p.relative_to(root)
        if p.is_file() and not any(part in {'build', '.gradle'} for part in relative.parts) and p.name != 'local.properties':
            archive.write(p, relative)
    for folder in ['components', 'lib', 'native-ui', 'scripts']:
        for p in (root / folder).rglob('*'):
            if p.is_file(): archive.write(p, p.relative_to(root))
    for name in ['package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml', 'tsconfig.json', 'app/globals.css', 'public/images/cat-emblem.png']:
        p = root / name
        if p.is_file(): archive.write(p, name)
`], { cwd: root, stdio: 'inherit' })
console.log(`Packaged signed debug APK and Android Studio source. SHA-256: ${checksum}`)
