import type { NoteComment, NoteVersion } from '../types/api'
import { Tabs } from '../components/Tabs'
import { Button } from '../components/Button'
import { Textarea } from '../components/Textarea'

type Tab = 'comments' | 'versions' | 'info'

type Props = {
  activeTab: Tab
  onTabChange: (tab: Tab) => void
  comments: NoteComment[]
  versions: NoteVersion[]
  commentInput: string
  onCommentInputChange: (value: string) => void
  onAddComment: () => void
  onResolveComment: (commentId: string) => void
  onReopenComment: (commentId: string) => void
  onRestoreVersion: (versionNumber: number) => void
  compact?: boolean
  disabled?: boolean
}

export function RightPanel(props: Props) {
  return (
    <aside className={props.compact ? 'w-full bg-white p-0' : 'w-80 border-l border-slate-200 bg-white p-3'}>
      <Tabs value={props.activeTab} onChange={props.onTabChange} />
      {props.activeTab === 'comments' && (
        <div className="space-y-2">
          <Textarea
            placeholder="Add a note-level comment..."
            value={props.commentInput}
            onChange={(event) => props.onCommentInputChange(event.target.value)}
            disabled={props.disabled}
          />
          <Button onClick={props.onAddComment} disabled={props.disabled}>
            Add comment
          </Button>
          {props.comments.map((comment) => (
            <div key={comment.id} className="rounded border border-slate-200 p-2 text-sm">
              <p className="break-words">{comment.content}</p>
              <div className="mt-2 flex gap-2">
                {!comment.resolvedAt ? (
                  <Button onClick={() => props.onResolveComment(comment.id)} disabled={props.disabled}>
                    Resolve
                  </Button>
                ) : (
                  <Button onClick={() => props.onReopenComment(comment.id)} disabled={props.disabled}>
                    Reopen
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
      {props.activeTab === 'versions' && (
        <div className="space-y-2">
          {props.versions.map((version) => (
            <div key={version.id} className="rounded border border-slate-200 p-2 text-sm">
              <p className="font-semibold">v{version.versionNumber}</p>
              <p className="break-words text-xs text-slate-500">{new Date(version.createdAt).toLocaleString()}</p>
              <Button className="mt-2" onClick={() => props.onRestoreVersion(version.versionNumber)} disabled={props.disabled}>
                Restore
              </Button>
            </div>
          ))}
        </div>
      )}
      {props.activeTab === 'info' && (
        <div className="text-sm text-slate-600">
          Backlinks and note metadata panel placeholder.
        </div>
      )}
    </aside>
  )
}

