// SFC 静态编译校验（沙箱内无法跑 esbuild/vite：用 @vue/compiler-sfc 直接解析+编译 script/template）
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

const files = process.argv.slice(2)
let failed = 0
for (const f of files) {
  const src = readFileSync(f, 'utf8')
  const { descriptor, errors } = parse(src, { filename: f })
  let errs = [...(errors || [])]
  try {
    if (descriptor.script || descriptor.scriptSetup) {
      const s = compileScript(descriptor, { id: f })
      if (s.errors?.length) errs.push(...s.errors)
    }
  } catch (e) {
    errs.push(e)
  }
  if (descriptor.template) {
    try {
      const t = compileTemplate({
        id: f,
        filename: f,
        source: descriptor.template.content,
        compilerOptions: { bindingMetadata: undefined }
      })
      if (t.errors?.length) errs.push(...t.errors)
    } catch (e) {
      errs.push(e)
    }
  }
  if (errs.length) {
    failed++
    console.error(`FAIL ${f}`)
    for (const e of errs) console.error('  -', (e && (e.message || e)) || e)
  } else {
    console.log(`OK   ${f}`)
  }
}
console.log(failed ? `\n${failed} file(s) failed` : '\nall SFC checks passed')
process.exit(failed ? 1 : 0)
