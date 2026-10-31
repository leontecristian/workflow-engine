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
  imports: [ConsoleIcon],
  templateUrl: "./page-header.html",
})
export class ConsolePageHeader {
  meta = input.required<PageMeta>();
  admin = input(false);
}
