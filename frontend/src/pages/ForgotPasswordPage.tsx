import { Link } from 'react-router-dom'
import { useState } from 'react'
import {
  AuthShell,
  AuthLogo,
  AuthCard,
  AuthInput,
  AuthButton,
} from '../features/auth/components'
import { MailIcon, ArrowForwardIcon, ArrowBackIcon } from '../features/auth/components/AuthIcons'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!email.trim()) return
    // No backend reset endpoint in MVP — safe client-only acknowledgment (no PII in logs).
    setSubmitted(true)
  }

  return (
    <AuthShell>
      <AuthLogo icon="book" />
      <AuthCard
        header={
          <>
            <h1 className="font-display text-headline-md text-on-surface">Reset your password</h1>
            <p className="mt-2 text-body-md text-on-surface-variant">
              {submitted
                ? 'If an account exists for that email, you will receive reset instructions shortly.'
                : 'Enter your email address and we will send you instructions to reset your password.'}
            </p>
          </>
        }
        footer={
          <Link
            to="/login"
            className="inline-flex items-center justify-center gap-2 text-label-md text-on-surface-variant transition-colors hover:text-primary"
          >
            <ArrowBackIcon className="h-4 w-4" />
            Back to login
          </Link>
        }
      >
        {submitted ? (
          <AuthButton variant="secondary" onClick={() => setSubmitted(false)}>
            Send another link
          </AuthButton>
        ) : (
          <form className="space-y-4" onSubmit={onSubmit}>
            <AuthInput
              label="Email address"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="name@company.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              leadingIcon={<MailIcon className="h-5 w-5" />}
            />
            <AuthButton type="submit">
              Send reset link
              <ArrowForwardIcon className="h-4 w-4" />
            </AuthButton>
          </form>
        )}
      </AuthCard>
    </AuthShell>
  )
}
