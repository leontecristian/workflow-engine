import { Component } from "@angular/core";
import { RouterLink } from "@angular/router";
import { MatButtonModule } from "@angular/material/button";
@Component({
  selector: "console-not-found",
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  templateUrl: "./not-found.html",
})
export class NotFoundPage {}
