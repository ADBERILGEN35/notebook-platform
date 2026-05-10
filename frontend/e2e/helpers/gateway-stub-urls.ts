/**
 * Match browser calls to the gateway change-requests API only — not Vite source paths like
 * `change-requests-api.ts` under `/src/` (broad Playwright URL globs can accidentally match those).
 */
export function matchGatewayChangeRequestsApi(url: URL): boolean {
  return (
    url.pathname === '/admin/enterprise/change-requests' ||
    url.pathname.startsWith('/admin/enterprise/change-requests/')
  )
}
