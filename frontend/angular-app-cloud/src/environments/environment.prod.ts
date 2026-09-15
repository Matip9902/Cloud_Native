export const environment = {
  production: true,
  azure: {
    clientId: '509a3118-7042-47ce-9c36-f7d7f5fda558',
    authority: 'https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de',
    redirectUri: 'https://REEMPLAZAR_DOMINIO_FRONTEND',
    scopes: [
      'api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.read',
      'api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.write',
    ],
  },
  api: {
    inventory: 'https://REEMPLAZAR_API_GATEWAY/api/v1/products',
    customers: 'https://REEMPLAZAR_API_GATEWAY/api/v1/customers',
    suppliers: 'https://REEMPLAZAR_API_GATEWAY/api/v1/suppliers',
    notifications: 'https://REEMPLAZAR_API_GATEWAY/api/v1/notifications',
  },
};
