export const e2eEnv = {
  apiBaseUrl: process.env.E2E_API_BASE_URL || 'http://localhost:8080',
  baseUrl: process.env.E2E_BASE_URL || 'http://localhost:5173',
  userEmail: process.env.E2E_USER_EMAIL || 'e2e-user@example.com',
  userPassword: process.env.E2E_USER_PASSWORD || 'Password1234!',
}

