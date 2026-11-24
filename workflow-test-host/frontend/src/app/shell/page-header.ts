import { Component, input, type Signal, type TemplateRef } from '@angular/core';

export interface PageHeaderContent {
  title: string;
  description?: string;
  meta: TemplateRef<unknown>;
}

export interface PageHeaderSource {
  readonly pageHeader: Signal<PageHeaderContent | null>;
  readonly pageHeaderParent: { label: string; path: string };
}

@Component({
  selector: 'console-detail-header',
  standalone: true,
  template: `
    <header class="page-header-inner">
      <h1>{{ title() }}</h1>
      @if (description()?.trim()) {
        <p class="page-header-description">{{ description() }}</p>
      }
      <div class="page-header-meta"><ng-content select="[meta]" /></div>
    </header>
  `,
  styleUrl: './page-header.scss',
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly description = input<string>();
}
