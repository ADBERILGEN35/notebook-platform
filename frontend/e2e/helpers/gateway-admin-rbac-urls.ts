/** Match gateway admin RBAC routes (avoid matching Vite `/src/...` paths). */
export function matchGatewayAdminRbacApi(url: URL): boolean {
  return url.pathname.startsWith('/admin/rbac')
}
