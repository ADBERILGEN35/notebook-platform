import { Button } from './Button'

type Props = {
  page: number
  hasNext: boolean
  hasPrevious: boolean
  onNext: () => void
  onPrevious: () => void
}

export function PaginationControls({ page, hasNext, hasPrevious, onNext, onPrevious }: Props) {
  return (
    <div className="mt-3 flex items-center justify-between">
      <Button disabled={!hasPrevious} onClick={onPrevious}>
        Previous
      </Button>
      <span className="text-xs text-slate-500">Page {page + 1}</span>
      <Button disabled={!hasNext} onClick={onNext}>
        Next
      </Button>
    </div>
  )
}

