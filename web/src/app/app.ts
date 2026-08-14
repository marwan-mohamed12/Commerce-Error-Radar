import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/services/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  host: {
    class: 'block h-full min-h-0 min-w-0',
  },
})
export class App {
  /** Apply stored theme before the inbox route renders. */
  private readonly theme = inject(ThemeService);
}
