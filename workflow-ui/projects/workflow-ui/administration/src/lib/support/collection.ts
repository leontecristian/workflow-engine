import { Component, input, output } from "@angular/core";
import { ConsoleIcon } from "@workflow-engine/ui";
import { ConsoleRecords } from "./records";
import { ConsolePagination } from "./pagination";
@Component({
  selector: "console-collection",
  standalone: true,
  styles: [".table-scroll{overflow:auto}"],
  imports: [ConsoleIcon, ConsoleRecords, ConsolePagination],
  templateUrl: "./collection.html",
})
export class ConsoleCollection {
  rows = input<any[]>([]);
  kind = input("");
  label = input("Records");
  icon = input("layers");
  size = input(50);
  next = input<string | null>(null);
  busy = input(false);
  reload = output<void>();
  advance = output<void>();
  sizeChange = output<number>();
  inspect = output<any>();
}
