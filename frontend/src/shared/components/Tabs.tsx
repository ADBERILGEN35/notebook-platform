type TabKey = 'comments' | 'versions' | 'info'

type Props = {
  value: TabKey
  onChange: (key: TabKey) => void
}

export function Tabs({ value, onChange }: Props) {
  const items: TabKey[] = ['comments', 'versions', 'info']
  return (
    <div className="mb-3 flex gap-2 border-b border-slate-200 pb-2">
      {items.map((item) => (
        <button
          key={item}
          onClick={() => onChange(item)}
          className={`rounded px-2 py-1 text-xs capitalize ${value === item ? 'bg-primary-100 text-primary-700' : 'text-slate-600'}`}
        >
          {item}
        </button>
      ))}
    </div>
  )
}

