import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { from, Observable, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Customer, NotificationItem, Product, Supplier } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(
    private readonly http: HttpClient,
    private readonly msalService: MsalService,
  ) {}

  getProducts(): Observable<Product[]> {
    return this.withAuthHeaders((headers) =>
      this.http.get<Product[]>(environment.api.inventory, { headers }),
    );
  }

  createProduct(product: Product): Observable<Product> {
    return this.withAuthHeaders((headers) =>
      this.http.post<Product>(environment.api.inventory, product, { headers }),
    );
  }

  updateProduct(product: Product): Observable<Product> {
    return this.withAuthHeaders((headers) =>
      this.http.put<Product>(`${environment.api.inventory}/${product.id}`, product, { headers }),
    );
  }

  deleteProduct(id: number): Observable<void> {
    return this.withAuthHeaders((headers) =>
      this.http.delete<void>(`${environment.api.inventory}/${id}`, { headers }),
    );
  }

  getCustomers(): Observable<Customer[]> {
    return this.withAuthHeaders((headers) =>
      this.http.get<Customer[]>(environment.api.customers, { headers }),
    );
  }

  getSuppliers(): Observable<Supplier[]> {
    return this.withAuthHeaders((headers) =>
      this.http.get<Supplier[]>(environment.api.suppliers, { headers }),
    );
  }

  getNotifications(): Observable<NotificationItem[]> {
    return this.withAuthHeaders((headers) =>
      this.http.get<NotificationItem[]>(environment.api.notifications, { headers }),
    );
  }

  private withAuthHeaders<T>(request: (headers: HttpHeaders) => Observable<T>): Observable<T> {
    const account =
      this.msalService.instance.getActiveAccount() ??
      this.msalService.instance.getAllAccounts()[0];

    return from(
      this.msalService.instance.acquireTokenSilent({
        account,
        scopes: environment.azure.scopes,
      }),
    ).pipe(
      switchMap((result) =>
        request(
          new HttpHeaders({
            Authorization: `Bearer ${result.accessToken}`,
          }),
        ),
      ),
    );
  }
}
