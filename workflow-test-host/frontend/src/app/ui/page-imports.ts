import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatTooltipModule } from "@angular/material/tooltip";
import { ConsolePageHeader } from "./page-header";
import { ConsoleFeedback } from "./feedback";
import { ConsoleCollection } from "./collection";
import { ConsoleIcon } from "./icon";
import { ConsoleStatus } from "./status";
import { ConsoleDetails } from "./details";
export const PAGE_IMPORTS = [
  CommonModule,
  FormsModule,
  RouterLink,
  MatButtonModule,
  MatCardModule,
  MatCheckboxModule,
  MatExpansionModule,
  MatFormFieldModule,
  MatInputModule,
  MatSelectModule,
  MatTooltipModule,
  ConsolePageHeader,
  ConsoleFeedback,
  ConsoleCollection,
  ConsoleIcon,
  ConsoleStatus,
  ConsoleDetails,
] as const;
