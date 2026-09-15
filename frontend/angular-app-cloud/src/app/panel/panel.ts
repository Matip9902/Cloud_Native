import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';
import { ApiService } from '../core/api.service';
import { Customer, NotificationItem, Product, Supplier } from '../core/models';

@Component({
  selector: 'app-panel',
  imports: [CommonModule, FormsModule],
  templateUrl: './panel.html',
  styleUrl: './panel.css',
})
export class Panel implements OnInit {
  account: AccountInfo | null = null;
  tokenClaims = {
    tenant: 'sin tenant',
    scopes: 'sin scopes',
    roles: 'sin roles configurados',
  };
  activeSection = 'products';
  loading = false;
  message = '';

  products: Product[] = [];
  customers: Customer[] = [];
  suppliers: Supplier[] = [];
  notifications: NotificationItem[] = [];

  productForm: Product = {
    name: '',
    category: '',
    price: 0,
    stock: 0,
    active: true,
  };

  constructor(
    private readonly msalService: MsalService,
    private readonly apiService: ApiService,
  ) {}

  ngOnInit(): void {
    this.account =
      this.msalService.instance.getActiveAccount() ??
      this.msalService.instance.getAllAccounts()[0] ??
      null;

    if (this.account) {
      this.msalService.instance.setActiveAccount(this.account);
      this.loadTokenClaims();
      this.loadData();
    }
  }

  logout(): void {
    this.msalService.logoutRedirect();
  }

  selectSection(section: string): void {
    this.activeSection = section;
    this.loadData();
  }

  loadData(): void {
    this.message = '';
    if (!this.account) {
      return;
    }

    if (this.activeSection === 'products') {
      this.loadProducts();
    }
    if (this.activeSection === 'customers') {
      this.loadCustomers();
    }
    if (this.activeSection === 'suppliers') {
      this.loadSuppliers();
    }
    if (this.activeSection === 'notifications') {
      this.loadNotifications();
    }
  }

  saveProduct(): void {
    this.loading = true;
    const request = this.productForm.id
      ? this.apiService.updateProduct(this.productForm)
      : this.apiService.createProduct(this.productForm);

    request.subscribe({
      next: () => {
        this.resetProductForm();
        this.loadProducts();
        this.message = 'Producto guardado correctamente.';
      },
      error: (error) => {
        this.loading = false;
        this.message = `No se pudo guardar el producto: ${this.readHttpError(error)}`;
      },
    });
  }

  editProduct(product: Product): void {
    this.productForm = { ...product };
  }

  deleteProduct(product: Product): void {
    if (!product.id) {
      return;
    }
    this.loading = true;
    this.apiService.deleteProduct(product.id).subscribe({
      next: () => {
        this.loadProducts();
        this.message = 'Producto eliminado.';
      },
      error: (error) => {
        this.loading = false;
        this.message = `No se pudo eliminar el producto: ${this.readHttpError(error)}`;
      },
    });
  }

  resetProductForm(): void {
    this.productForm = {
      name: '',
      category: '',
      price: 0,
      stock: 0,
      active: true,
    };
  }

  private loadProducts(): void {
    this.loading = true;
    this.apiService.getProducts().subscribe({
      next: (products) => {
        this.products = products;
        this.loading = false;
      },
      error: (error) => this.handleLoadError('productos', error),
    });
  }

  private loadCustomers(): void {
    this.loading = true;
    this.apiService.getCustomers().subscribe({
      next: (customers) => {
        this.customers = customers;
        this.loading = false;
      },
      error: (error) => this.handleLoadError('clientes', error),
    });
  }

  private loadSuppliers(): void {
    this.loading = true;
    this.apiService.getSuppliers().subscribe({
      next: (suppliers) => {
        this.suppliers = suppliers;
        this.loading = false;
      },
      error: (error) => this.handleLoadError('proveedores', error),
    });
  }

  private loadNotifications(): void {
    this.loading = true;
    this.apiService.getNotifications().subscribe({
      next: (notifications) => {
        this.notifications = notifications;
        this.loading = false;
      },
      error: (error) => this.handleLoadError('notificaciones', error),
    });
  }

  private handleLoadError(resource: string, error: unknown): void {
    this.loading = false;
    this.message = `No se pudieron cargar ${resource}: ${this.readHttpError(error)}`;
  }

  private readErrorMessage(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }
    return String(error);
  }

  private readHttpError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'sin respuesta del backend o bloqueo CORS';
      }
      if (error.status === 401) {
        return '401 no autorizado, el token no fue aceptado';
      }
      if (error.status === 403) {
        return '403 sin permisos, falta scope o rol requerido';
      }
      return `${error.status} ${error.statusText || 'error del backend'}`;
    }
    return this.readErrorMessage(error);
  }

  private loadTokenClaims(): void {
    const tokenEntry =
      Object.entries(sessionStorage).find(([key]) => key.includes('accesstoken')) ??
      Object.entries(localStorage).find(([key]) => key.includes('accesstoken'));

    if (!tokenEntry) {
      return;
    }

    try {
      const tokenData = JSON.parse(tokenEntry[1]) as { secret?: string };
      const token = tokenData.secret;
      if (!token) {
        return;
      }

      const payload = JSON.parse(atob(token.split('.')[1])) as {
        tid?: string;
        scp?: string;
        roles?: string[];
      };

      this.tokenClaims = {
        tenant: payload.tid || 'sin tenant',
        scopes: payload.scp || 'sin scopes',
        roles: payload.roles?.join(', ') || 'sin roles configurados',
      };
    } catch {
      this.tokenClaims = {
        tenant: 'no disponible',
        scopes: 'no disponible',
        roles: 'no disponible',
      };
    }
  }
}
