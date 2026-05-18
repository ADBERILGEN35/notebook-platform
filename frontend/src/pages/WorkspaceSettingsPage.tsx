import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../shared/api/api-client'
import { getWorkspace, updateWorkspace } from '../features/workspaces/workspace-api'
import { PageHeader } from '../shared/components/PageHeader'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { SettingsSection } from '../shared/components/SettingsSection'
import { DangerZoneCard } from '../shared/components/DangerZoneCard'
import { InfoRow } from '../shared/components/InfoRow'
import { Input } from '../shared/components/Input'
import { Button } from '../shared/components/Button'
import { LoadingState } from '../shared/components/LoadingState'
import { ErrorState } from '../shared/components/ErrorState'
import { AccessDeniedState } from '../features/access/components/AccessDeniedState'

export function WorkspaceSettingsPage() {
  const { workspaceId } = useParams()
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')

  const query = useQuery({
    queryKey: ['workspace', workspaceId],
    queryFn: () => getWorkspace(workspaceId!),
    enabled: Boolean(workspaceId),
  })

  useEffect(() => {
    if (query.data) {
      setName(query.data.name)
      setSlug(query.data.slug)
    }
  }, [query.data])

  const saveMutation = useMutation({
    mutationFn: () => updateWorkspace(workspaceId!, { name: name.trim(), slug: slug.trim() }),
    onSuccess: () => void query.refetch(),
  })

  if (!workspaceId) return null

  if (query.isError && query.error instanceof ApiError && query.error.status === 403) {
    return (
      <ResponsiveContent>
        <AccessDeniedState message="You do not have permission to change settings for this workspace." />
      </ResponsiveContent>
    )
  }

  return (
    <ResponsiveContent>
      <PageHeader title="Workspace settings" subtitle="General preferences, branding, and policies." />
      <nav className="mb-4 text-label-md">
        <Link to={`/app/workspaces/${workspaceId}`} className="text-primary hover:underline">
          ← Back to workspace hub
        </Link>
        <span className="mx-2 text-on-surface-variant">·</span>
        <Link to={`/app/workspaces/${workspaceId}/members`} className="text-primary hover:underline">
          Members
        </Link>
      </nav>

      {query.isLoading ? <LoadingState label="Loading workspace…" /> : null}
      {query.isError ? <ErrorState error={query.error} /> : null}

      {query.data ? (
        <div className="space-y-8">
          <SettingsSection title="General" description="Name and URL slug for this workspace.">
            <label className="mb-3 block text-label-md text-on-surface-variant">
              Workspace name
              <Input className="mt-1" value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <label className="mb-4 block text-label-md text-on-surface-variant">
              Slug
              <Input className="mt-1" value={slug} onChange={(e) => setSlug(e.target.value)} />
            </label>
            <Button
              type="button"
              className="bg-primary text-white hover:bg-primary-container"
              disabled={saveMutation.isPending || !name.trim() || !slug.trim()}
              onClick={() => saveMutation.mutate()}
            >
              Save changes
            </Button>
            {saveMutation.isError ? <ErrorState error={saveMutation.error} className="mt-3" /> : null}
          </SettingsSection>

          <SettingsSection title="Branding" description="Logo and accent colors (preview).">
            <InfoRow label="Logo" value="Upload coming soon" hint="Workspace branding is not configured in this environment." />
            <InfoRow label="Accent" value="Primary indigo (design system default)" />
          </SettingsSection>

          <SettingsSection title="Security & policies" description="Retention and notification policies are managed per workspace.">
            <InfoRow
              label="Notification policies"
              value={
                <Link to="/app/settings/notifications" className="text-primary hover:underline">
                  Open notification settings
                </Link>
              }
            />
          </SettingsSection>

          <DangerZoneCard
            title="Danger zone"
            description="Archiving a workspace is irreversible for members. This action is disabled in the foundation UI."
            actions={
              <Button type="button" disabled title="Archive workspace (not enabled)">
                Archive workspace
              </Button>
            }
          />
        </div>
      ) : null}
    </ResponsiveContent>
  )
}
