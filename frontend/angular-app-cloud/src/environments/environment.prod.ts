const apiBaseUrl = 'https://xwgnimeoa5.execute-api.us-east-1.amazonaws.com';

export const environment = {
  production: true,
  azure: {
    clientId: '509a3118-7042-47ce-9c36-f7d7f5fda558',
    authority: 'https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de',
    redirectUri: window.location.origin,
    scopes: [
      'api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.read',
      'api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.write',
    ],
  },
  api: {
    audits: `${apiBaseUrl}/api/v1/audits`,
    metrics: `${apiBaseUrl}/api/v1/metrics`,
    inventory: `${apiBaseUrl}/api/v1/products`,
    customers: `${apiBaseUrl}/api/v1/customers`,
    suppliers: `${apiBaseUrl}/api/v1/suppliers`,
    notifications: `${apiBaseUrl}/api/v1/notifications`,
  },
};
