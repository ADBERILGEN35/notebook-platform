import { useRef, type KeyboardEvent } from 'react'

const DIGITS = 6

type MfaCodeInputProps = {
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  idPrefix?: string
}

export function MfaCodeInput({ value, onChange, disabled, idPrefix = 'mfa-digit' }: MfaCodeInputProps) {
  const refs = useRef<Array<HTMLInputElement | null>>([])
  const digits = value.padEnd(DIGITS, ' ').slice(0, DIGITS).split('')

  const updateAt = (index: number, char: string) => {
    const next = digits.map((d, i) => (i === index ? char : d === ' ' ? '' : d))
    onChange(next.join('').replace(/\s/g, '').slice(0, DIGITS))
  }

  const onKeyDown = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Backspace' && !digits[index]?.trim() && index > 0) {
      refs.current[index - 1]?.focus()
    }
  }

  return (
    <div className="flex justify-between gap-2" role="group" aria-label="Authentication code">
      {digits.map((digit, index) => (
        <input
          key={index}
          ref={(el) => {
            refs.current[index] = el
          }}
          id={`${idPrefix}-${index}`}
          type="text"
          inputMode="numeric"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          maxLength={1}
          disabled={disabled}
          value={digit.trim()}
          placeholder="·"
          aria-label={`Digit ${index + 1} of ${DIGITS}`}
          className="h-14 w-11 rounded-lg border border-outline-variant bg-surface text-center font-display text-headline-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary disabled:opacity-60 sm:w-12"
          onChange={(event) => {
            const char = event.target.value.replace(/\D/g, '').slice(-1)
            updateAt(index, char)
            if (char && index < DIGITS - 1) refs.current[index + 1]?.focus()
          }}
          onKeyDown={(event) => onKeyDown(index, event)}
        />
      ))}
    </div>
  )
}
