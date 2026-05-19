const SECRET_VALUE =
  /(?:^|[\s:=])(eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+|Bearer\s+[A-Za-z0-9._-]{20,}|ghp_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,})/

const YAML_SECRET_KEY = /(password|token|secret|apiKey|clientSecret|privateKey|webhook)/i

/** Mask secret-looking substrings in a diff line for safe UI render. */
export function maskDiffLineContent(line: string): string {
  let out = line
  if (SECRET_VALUE.test(out)) {
    out = out.replace(SECRET_VALUE, (m) => m.replace(/[^\s:=]+$/u, '***masked***'))
  }
  const kv = out.match(/^(\s*[-+ ]?\s*)([\w.-]+):\s*(.+)$/)
  if (kv && YAML_SECRET_KEY.test(kv[2])) {
    return `${kv[1]}${kv[2]}: "***masked***"`
  }
  return out
}
