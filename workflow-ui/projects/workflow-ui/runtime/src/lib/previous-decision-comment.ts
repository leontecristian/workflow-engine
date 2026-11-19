import { DestroyRef, Directive, ElementRef, afterNextRender, inject, signal } from '@angular/core';

@Directive({
  selector: '[workflowPreviousDecisionComment]',
  exportAs: 'previousDecisionComment',
  standalone: true,
  host: { '[class.comment-clamped]': '!expanded()' },
})
export class PreviousDecisionComment {
  readonly expanded = signal(false);
  readonly overflows = signal(false);

  constructor() {
    const paragraph = inject(ElementRef<HTMLParagraphElement>).nativeElement;
    const destroyRef = inject(DestroyRef);
    afterNextRender({
      read: () => {
        const observer = new ResizeObserver(() => {
          if (!this.expanded()) {
            this.overflows.set(paragraph.scrollHeight > paragraph.clientHeight);
          }
        });
        observer.observe(paragraph);
        destroyRef.onDestroy(() => observer.disconnect());
      },
    });
  }
}
