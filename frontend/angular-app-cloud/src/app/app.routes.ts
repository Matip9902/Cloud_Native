import { Routes } from '@angular/router';
import { MsalGuard } from '@azure/msal-angular';
import { Panel } from './panel/panel';

export const routes: Routes = [
  {
    path: 'panel',
    component: Panel,
    canActivate: [MsalGuard],
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'panel',
  },
  {
    path: '**',
    redirectTo: 'panel',
  },
];
