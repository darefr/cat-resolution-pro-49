import { build } from 'esbuild'
import { mkdir, copyFile, writeFile, readdir } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const assets = path.join(root, 'android/app/src/main/assets')
await mkdir(path.join(assets, 'images'), { recursive: true })
await mkdir(path.join(assets, 'fonts'), { recursive: true })
await mkdir(path.join(root, 'android/app/src/main/res/drawable'), { recursive: true })
await Promise.all([
  copyFile(path.join(root, 'native-ui/index.html'), path.join(assets, 'index.html')),
  copyFile(path.join(root, 'public/images/cat-emblem.png'), path.join(assets, 'images/cat-emblem.png')),
  copyFile(path.join(root, 'public/images/cat-emblem.png'), path.join(root, 'android/app/src/main/res/drawable/cat_emblem.png')),
])

await build({
  entryPoints: [path.join(root, 'native-ui/entry.tsx')],
  outfile: path.join(assets, 'native.js'),
  bundle: true,
  minify: true,
  format: 'iife',
  platform: 'browser',
  target: ['chrome111'],
  jsx: 'automatic',
  define: { 'process.env.NODE_ENV': '"production"' },
  tsconfig: path.join(root, 'tsconfig.json'),
  logLevel: 'info',
})
execFileSync('pnpm', ['exec', 'tailwindcss', '-i', 'app/globals.css', '-o', path.join(assets, 'native.css'), '--minify'], { cwd: root, stdio: 'inherit' })

let fontCss = ''
for (const family of ['barlow', 'barlow-condensed']) {
  const fontDir = path.join(root, 'node_modules/@fontsource', family, 'files')
  const names = (await readdir(fontDir)).filter((name) => new RegExp(`^${family}-latin-(400|500|600|700|800|900)-(normal|italic)\\.woff2$`).test(name))
  for (const name of names) {
    await copyFile(path.join(fontDir, name), path.join(assets, 'fonts', name))
    const [, weight, style] = name.match(/-(\d+)-(normal|italic)\.woff2$/)
    const label = family === 'barlow' ? 'Barlow' : 'Barlow Condensed'
    fontCss += `@font-face{font-family:'${label}';font-style:${style};font-weight:${weight};font-display:swap;src:url('/fonts/${name}') format('woff2');}\n`
  }
}
await writeFile(path.join(assets, 'fonts.css'), fontCss)
console.log('Offline Android interface compiled with bundled images and fonts.')
