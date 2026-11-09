import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo, AuthenticationResult, IPublicClientApplication } from '@azure/msal-browser';
import { of, Subject, throwError } from 'rxjs';
import { App } from './app';

describe('App authentication', () => {
  const account = (id: string): AccountInfo => ({
    homeAccountId: id, localAccountId: id, tenantId: 'tenant', environment: 'login.microsoftonline.com', username: `${id}@example.com`,
  });
  let instance: jasmine.SpyObj<IPublicClientApplication>;
  let msal: jasmine.SpyObj<MsalService>;
  beforeEach(async () => {
    instance = jasmine.createSpyObj('instance', ['getActiveAccount', 'getAllAccounts', 'setActiveAccount']);
    instance.getActiveAccount.and.returnValue(null);
    instance.getAllAccounts.and.returnValue([]);
    msal = jasmine.createSpyObj('MsalService', ['initialize', 'handleRedirectObservable'], { instance });
    msal.initialize.and.returnValue(of(undefined));
    msal.handleRedirectObservable.and.returnValue(of(null));
    await TestBed.configureTestingModule({
      imports: [App], providers: [provideRouter([]), { provide: MsalService, useValue: msal }],
    }).compileComponents();
  });
  function render() {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return fixture;
  }
  it('waits for initialization and gives the redirect account priority', () => {
    const initialized = new Subject<void>();
    const redirected = account('redirected');
    msal.initialize.and.returnValue(initialized);
    instance.getActiveAccount.and.returnValue(account('active'));
    msal.handleRedirectObservable.and.returnValue(of({ account: redirected } as AuthenticationResult));
    render();
    expect(msal.handleRedirectObservable).not.toHaveBeenCalled();
    initialized.next();
    initialized.complete();
    expect(msal.handleRedirectObservable).toHaveBeenCalledOnceWith({ navigateToLoginRequestUrl: false });
    expect(instance.setActiveAccount).toHaveBeenCalledOnceWith(redirected);
  });
  it('keeps the active account when there is no redirect result', () => {
    const active = account('active');
    instance.getActiveAccount.and.returnValue(active);
    instance.getAllAccounts.and.returnValue([account('cached')]);
    render();
    expect(instance.setActiveAccount).toHaveBeenCalledOnceWith(active);
  });
  it('selects the first cached account when there is no active account', () => {
    const first = account('first');
    instance.getAllAccounts.and.returnValue([first, account('second')]);
    render();
    expect(instance.setActiveAccount).toHaveBeenCalledOnceWith(first);
  });
  it('renders the router outlet without an account when signed out', () => {
    const fixture = render();
    expect(instance.setActiveAccount).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
  });
  it('reports initialization failure without processing a redirect', () => {
    const error = new Error('Initialization failed');
    const logged = spyOn(console, 'error');
    msal.initialize.and.returnValue(throwError(() => error));
    render();
    expect(logged).toHaveBeenCalledOnceWith('No se pudo inicializar MSAL', error);
    expect(msal.handleRedirectObservable).not.toHaveBeenCalled();
  });
  it('reports redirect failure without selecting an account', () => {
    const error = new Error('Redirect failed');
    const logged = spyOn(console, 'error');
    msal.handleRedirectObservable.and.returnValue(throwError(() => error));
    render();
    expect(logged).toHaveBeenCalledOnceWith('No se pudo completar el inicio de sesion con Azure AD', error);
    expect(instance.setActiveAccount).not.toHaveBeenCalled();
  });
});
