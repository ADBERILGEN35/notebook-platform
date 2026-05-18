import type { ButtonHTMLAttributes, PropsWithChildren } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost'

type AuthButtonProps = PropsWithChildren<
  ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: Variant
    fullWidth?: boolean
  }
>

const variantClass: Record<Variant, string> = {
  primary:
    'border-transparent bg-primary text-white hover:bg-primary-container focus-visible:ring-2 focus-visible:ring-primary-fixed-dim',
  secondary:
    'border-outline-variant bg-surface-container-lowest text-on-surface hover:bg-surface-container-low',
  ghost: 'border-transparent bg-transparent text-primary hover:underline',
}

export function AuthButton({
  children,
  variant = 'primary',
  fullWidth = true,
  className = '',
  type = 'button',
  disabled,
  ...props
}: AuthButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled}
      {...props}
      className={`inline-flex items-center justify-center gap-2 rounded-lg border px-4 py-2.5 text-body-md font-medium transition-all active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 ${
        fullWidth ? 'w-full' : ''
      } ${variantClass[variant]} ${className}`}
    >
      {children}
    </button>
  )
}
