import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { AuthenticationResult } from '@azure/msal-browser';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  constructor(private readonly msalService: MsalService) {}

  ngOnInit(): void {
    this.msalService.initialize().subscribe({
      next: () => this.handleLoginRedirect(),
      error: (error) => console.error('No se pudo inicializar MSAL', error),
    });
  }

  private handleLoginRedirect(): void {
    this.msalService.handleRedirectObservable({ navigateToLoginRequestUrl: false }).subscribe({
      next: (result: AuthenticationResult | null) => {
        const account =
          result?.account ??
          this.msalService.instance.getActiveAccount() ??
          this.msalService.instance.getAllAccounts()[0] ??
          null;

        if (account) {
          this.msalService.instance.setActiveAccount(account);
        }
      },
      error: (error) => console.error('No se pudo completar el inicio de sesion con Azure AD', error),
    });
  }
}
