// by Jeremy Posada
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastsComponent } from '@shared/components/toasts/toasts.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastsComponent],
  templateUrl: './app.html',
})
export class App {}
