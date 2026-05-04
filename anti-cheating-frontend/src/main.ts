import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

// Polyfill for SockJS - ensure global is defined
(window as any).global = window;

// Auto-reload on dynamic import failures (common Vite/Angular dev server issue when chunks change)
window.addEventListener('error', (e) => {
  if (e.message && (e.message.includes('Failed to fetch dynamically imported module') || e.message.includes('Importing a module script failed'))) {
    console.warn('Chunk outdated, reloading page...');
    window.location.reload();
  }
});

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
