import { signal } from "@angular/core";
/** Per-component async feedback only; forms, queries and paging belong to the page. */
export function pageState() {
  const busy = signal(false),
    error = signal(""),
    notice = signal("");
  const run = async (action: () => Promise<void>) => {
    busy.set(true);
    error.set("");
    try {
      await action();
    } catch (e) {
      error.set(e instanceof Error ? e.message : String(e));
    } finally {
      busy.set(false);
    }
  };
  return { busy, error, notice, run };
}
