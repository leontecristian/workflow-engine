import { Component, input, type Signal, type TemplateRef } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

export interface PageHeaderContent {
  title: string;
  description?: string;
  meta?: TemplateRef<unknown>;
}

export interface PageHeaderSource {
  readonly pageHeader: Signal<PageHeaderContent | null>;
  readonly pageHeaderParent?: { label: string; path: string };
}

@Component({
  selector: 'console-detail-header',
  standalone: true,
  imports: [NgTemplateOutlet],
  template: `
    <header class="page-header-inner">
      <h1>{{ title() }}</h1>
      @if (description()?.trim()) {
        <p class="page-header-description">{{ description() }}</p>
      }
      @if (meta(); as content) {
        <div class="page-header-meta"><ng-container [ngTemplateOutlet]="content" /></div>
      }
    </header>
  `,
  styleUrl: './page-header.scss',
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly meta = input<TemplateRef<unknown>>();
}
