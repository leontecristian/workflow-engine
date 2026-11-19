/** Match Java String.isBlank without trimming or otherwise changing submitted text. */
export function isBlankComment(text: string): boolean {
  return /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u.test(text);
}

export function isBlankResult(text: string): boolean {
  return /^[ \t\r\n]*$/.test(text);
}
