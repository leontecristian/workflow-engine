import { Component, input, output } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatSelectModule } from "@angular/material/select";
import { MatTooltipModule } from "@angular/material/tooltip";
import { ConsoleIcon } from "@workflow-engine/ui";
@Component({
  selector: "console-pagination",
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTooltipModule,
    ConsoleIcon,
  ],
  templateUrl: "./pagination.html",
})
export class ConsolePagination {
  size = input(50);
  next = input<string | null>(null);
  busy = input(false);
  reload = output<void>();
  advance = output<void>();
  sizeChange = output<number>();
}
