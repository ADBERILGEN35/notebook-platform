/** User-facing trust copy — not admin/security incident UI. */
export function EnterpriseTrustPanel() {
  return (
    <aside
      className="rounded-xl border border-outline-variant bg-surface-container-low p-4"
      aria-label="Platform security and collaboration"
    >
      <h2 className="font-display text-headline-sm text-on-surface">Built for teams</h2>
      <ul className="mt-3 space-y-2 text-body-md text-on-surface-variant">
        <li className="flex gap-2">
          <span className="text-primary" aria-hidden>
            🔒
          </span>
          <span>Role-based access and workspace membership enforced by the API.</span>
        </li>
        <li className="flex gap-2">
          <span className="text-primary" aria-hidden>
            🤝
          </span>
          <span>Invite teammates after your first workspace is created.</span>
        </li>
        <li className="flex gap-2">
          <span className="text-primary" aria-hidden>
            ✓
          </span>
          <span>Audit-friendly activity stays in your workspace — no secrets in the browser UI.</span>
        </li>
      </ul>
    </aside>
  )
}
