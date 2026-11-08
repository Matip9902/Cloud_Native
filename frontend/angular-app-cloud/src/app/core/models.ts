export interface Product {
  id?: number;
  name: string;
  category: string;
  price: number;
  stock: number;
  active: boolean;
}

export interface Customer {
  id?: number;
  fullName: string;
  email: string;
  phone?: string;
}

export interface Supplier {
  id?: number;
  companyName: string;
  contactEmail: string;
  phone?: string;
}

export interface NotificationItem {
  id?: number;
  recipient: string;
  message: string;
  readStatus: boolean;
}

export interface AuditRecord {
  eventId: string;
  eventType: string;
  productId: number;
  productName: string;
  occurredAt: string;
  receivedAt: string;
}
