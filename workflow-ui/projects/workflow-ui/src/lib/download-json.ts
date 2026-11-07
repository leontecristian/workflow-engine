/** Export already-authorized exact text, without a numeric parse/serialize round trip. */
export function downloadJson(text: string, filename: string): void {
  const url = URL.createObjectURL(new Blob([text], {type: "application/json"}));
  try {
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
  } finally { URL.revokeObjectURL(url); }
}
