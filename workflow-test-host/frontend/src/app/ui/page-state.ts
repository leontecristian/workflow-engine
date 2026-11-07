import { DestroyRef, signal } from "@angular/core";
/** Angular 19-compatible destruction predicate for async responses outside RxJS subscriptions. */
export function destroyedState(destroyRef: DestroyRef): () => boolean {
  let destroyed = false;
  destroyRef.onDestroy(() => { destroyed = true; });
  return () => destroyed;
}
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
