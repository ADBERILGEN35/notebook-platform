import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { authUserFromMeResponse, me, signup, signupSchema } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import {
  AuthShell,
  AuthCard,
  AuthInput,
  AuthButton,
  SsoButton,
  PasswordStrength,
} from '../features/auth/components'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { isSsoEnabled } from '../shared/config/sso-feature-flags'
import { API_BASE_URL } from '../shared/api/api-client'
import { MailIcon, LockIcon, PersonIcon } from '../features/auth/components/AuthIcons'

function AuthDivider() {
  return (
    <div className="my-6 flex items-center">
      <span className="h-px flex-1 bg-outline-variant" />
      <span className="px-4 text-label-md text-on-surface-variant">OR</span>
      <span className="h-px flex-1 bg-outline-variant" />
    </div>
  )
}

export function RegisterPage() {
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const setUser = useAuthStore((state) => state.setUser)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [termsAccepted, setTermsAccepted] = useState(false)
  const ssoEnabled = isSsoEnabled()

  const mutation = useMutation({
    mutationFn: signup,
    onSuccess: async (data) => {
      setSession({
        accessToken: data.accessToken ?? null,
        refreshToken: data.refreshToken ?? null,
        user: data.user,
      })
      try {
        const profile = await me()
        setUser(authUserFromMeResponse(profile, data.user.status))
      } catch {
        // ignore enrichment failure
      }
      navigate('/app')
    },
  })

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!termsAccepted) return
    const parsed = signupSchema.safeParse({ name, email, password, avatarUrl: '' })
    if (!parsed.success) return
    mutation.mutate(parsed.data)
  }

  const startSso = () => {
    if (!ssoEnabled) return
    const returnUrl = encodeURIComponent('/sso/callback?next=/app')
    window.location.assign(`${API_BASE_URL}/auth/sso/generic-oidc/authorize?returnUrl=${returnUrl}`)
  }

  return (
    <AuthShell>
      <AuthCard
        header={
          <div className="flex flex-col items-center">
            <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary-container text-white">
              <PersonIcon className="h-6 w-6" />
            </div>
            <h1 className="font-display text-headline-md text-on-surface">Create your workspace</h1>
            <p className="mt-1 text-body-md text-on-surface-variant">Secure access to the Notebook Platform</p>
          </div>
        }
        footer={
          <p className="text-center text-body-md text-on-surface-variant">
            Already have an account?{' '}
            <Link to="/login" className="font-semibold text-primary hover:underline">
              Log in
            </Link>
          </p>
        }
      >
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          <AuthInput
            label="Full name"
            name="name"
            autoComplete="name"
            placeholder="John Doe"
            value={name}
            onChange={(event) => setName(event.target.value)}
            leadingIcon={<PersonIcon className="h-5 w-5" />}
          />
          <AuthInput
            label="Work email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="name@company.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            leadingIcon={<MailIcon className="h-5 w-5" />}
          />
          <div>
            <AuthInput
              label="Password"
              name="password"
              type="password"
              autoComplete="new-password"
              placeholder="••••••••"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              leadingIcon={<LockIcon className="h-5 w-5" />}
            />
            <PasswordStrength password={password} />
          </div>
          <label className="flex items-start gap-3 text-body-md text-on-surface-variant">
            <input
              type="checkbox"
              className="mt-1 h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
              checked={termsAccepted}
              onChange={(event) => setTermsAccepted(event.target.checked)}
            />
            <span>
              I agree to the Terms of Service and Privacy Policy.
            </span>
          </label>
          {mutation.isError ? <ErrorAlert error={mutation.error} /> : null}
          <AuthButton type="submit" disabled={mutation.isPending || !termsAccepted}>
            {mutation.isPending ? 'Creating account…' : 'Create account'}
          </AuthButton>
        </form>
        {ssoEnabled ? (
          <>
            <AuthDivider />
            <SsoButton label="Sign up with SSO" onClick={startSso} />
          </>
        ) : null}
      </AuthCard>
    </AuthShell>
  )
}
