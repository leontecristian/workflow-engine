import { Component, input } from "@angular/core";
import { MatIconModule } from "@angular/material/icon";
export interface PageMeta {
  title: string;
  eyebrow: string;
  description: string;
  icon: string;
  listTitle?: string;
  listDescription?: string;
}
@Component({
  selector: "console-page-header",
  standalone: true,
  styles: [".page-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:24px}h1{margin:6px 0}.page-context{display:flex;gap:8px;align-items:center}.page-actions{display:none}@media(max-width:760px){.page-heading{align-items:flex-start;flex-direction:column}}", `
    .page-heading.with-title-actions { display:grid; grid-template-columns:minmax(0,1fr) auto auto; gap:0 16px; align-items:center; }
    .with-title-actions .heading-copy { display:contents; }
    .with-title-actions .eyebrow { grid-column:1 / -1; grid-row:1; }
    .with-title-actions h1 { grid-column:1; grid-row:2; }
    .with-title-actions .page-context { grid-column:2; grid-row:2; }
    .with-title-actions .page-actions { display:flex; grid-column:3; grid-row:2; align-items:center; }
    .with-title-actions .description { grid-column:1 / -1; grid-row:3; }
    @media(max-width:760px) {
      .page-heading.with-title-actions { grid-template-columns:minmax(0,1fr) auto; }
      .with-title-actions .page-actions { grid-column:2; }
      .with-title-actions .page-context { grid-column:1 / -1; grid-row:3; }
      .with-title-actions .description { grid-row:4; }
    }
    @media(max-width:480px) {
      .page-heading.with-title-actions { grid-template-columns:minmax(0,1fr); }
      .with-title-actions .page-actions { grid-column:1; grid-row:4; margin-top:12px; }
      .with-title-actions .description { grid-row:5; }
    }
  `],
  imports: [MatIconModule],
  templateUrl: "./page-header.html",
})
export class ConsolePageHeader {
  meta = input.required<PageMeta>();
  admin = input(false);
  context = input("");
  titleActions = input(false);
}
