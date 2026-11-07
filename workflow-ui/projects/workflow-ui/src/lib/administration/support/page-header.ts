import { Component, input } from "@angular/core";
import { ConsoleIcon } from "./icon";
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
  styles: [".page-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:24px}h1{margin:6px 0}.page-context{display:flex;gap:8px;align-items:center}@media(max-width:760px){.page-heading{align-items:flex-start;flex-direction:column}}"],
  imports: [ConsoleIcon],
  templateUrl: "./page-header.html",
})
export class ConsolePageHeader {
  meta = input.required<PageMeta>();
  admin = input(false);
  context = input("");
}
