type Collaborator = {
  id: string
  label: string
}

type CollaboratorAvatarStackProps = {
  collaborators: Collaborator[]
  maxVisible?: number
}

export function CollaboratorAvatarStack({ collaborators, maxVisible = 4 }: CollaboratorAvatarStackProps) {
  const visible = collaborators.slice(0, maxVisible)
  const overflow = collaborators.length - visible.length
  return (
    <ul className="flex -space-x-2" aria-label={`${collaborators.length} collaborators`}>
      {visible.map((person) => (
        <li key={person.id}>
          <span
            className="inline-flex h-8 w-8 items-center justify-center rounded-full border-2 border-surface-container-lowest bg-primary text-label-md font-semibold text-white"
            title={person.label}
          >
            {person.label.trim().charAt(0).toUpperCase() || '?'}
          </span>
        </li>
      ))}
      {overflow > 0 ? (
        <li>
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-full border-2 border-surface-container-lowest bg-surface-container-high text-label-md text-on-surface-variant">
            +{overflow}
          </span>
        </li>
      ) : null}
    </ul>
  )
}
