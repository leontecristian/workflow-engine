import { Component, input } from "@angular/core";
@Component({
  selector: "console-icon",
  standalone: true,
  template: `<svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path [attr.d]="paths[name()] || paths['layers']" />
  </svg>`,
  styles: [
    `
      :host {
        display: inline-flex;
        width: 20px;
        height: 20px;
        flex-shrink: 0;
        vertical-align: middle;
      }
      svg {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class ConsoleIcon {
  name = input("layers");
  readonly paths: Record<string, string> = {
    layers: "m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5",
    workflow: "M5 3h5v5H5zM14 16h5v5h-5zM7.5 8v7h9v1M14 5h6M17 2v6",
    tasks: "M9 4h10v17H5V4h4M9 2h6v4H9zM8 11l2 2 5-4M8 17h8",
    replay: "M3 10a9 9 0 1 1 2 8M3 4v6h6M12 7v5l3 2",
    catalog: "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z",
    start: "m9 5 11 7-11 7V5ZM4 5v14",
    history: "M8 3h12v18H4V7l4-4ZM8 3v5H4M8 12h8M8 16h8",
    events: "m13 2-8 12h6l-1 8 9-13h-7l1-7Z",
    inbox: "M4 4h16l2 12v4H2v-4L4 4ZM2 15h6l2 3h4l2-3h6",
    settings:
      "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2",
    chevron: "m9 5 7 7-7 7",
    down: "m6 9 6 6 6-6",
    plus: "M12 5v14M5 12h14",
    upload: "M12 16V3m-5 5 5-5 5 5M4 16v5h16v-5",
    search: "M21 21l-6-6M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14Z",
    refresh: "M20 7a9 9 0 1 0 1 8M20 2v6h-6",
    logout: "M9 4H3v16h6M8 12h13m-5-5 5 5-5 5",
    menu: "M4 6h16M4 12h16M4 18h16",
    shield: "m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6l9-4Zm-4 10 3 3 5-6",
    check: "m5 12 4 4L19 6",
    info: "M12 11v6M12 7h.01M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Z",
    code: "m8 5-6 7 6 7M16 5l6 7-6 7M14 3l-4 18",
    file: "M14 2H4v20h16V8l-6-6ZM14 2v6h6M8 13h8M8 17h6",
  };
}
