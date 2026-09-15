export const environment = {
  production: false,
  azure: {
    clientId: '509a3118-7042-47ce-9c36-f7d7f5fda558',
    authority: 'https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de',
    redirectUri: 'http://localhost:4200',
    scopes: [
      'api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.read',
      'api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.write',
    ],
  },
  api: {
    inventory: '/api/v1/products',
    customers: '/api/v1/customers',
    suppliers: '/api/v1/suppliers',
    notifications: '/api/v1/notifications',
  },
};
