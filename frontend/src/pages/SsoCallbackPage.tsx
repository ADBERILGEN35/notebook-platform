import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { authUserFromMeResponse, me } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import { AuthShell, AuthStatusScreen, AuthButton } from '../features/auth/components'
import { ssoErrorMessage } from '../features/auth/sso-errors'
import type { AuthStatusVariant } from '../features/auth/components/AuthStatusScreen'

export function SsoCallbackPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const setSession = useAuthStore((state) => state.setSession)
  const setUser = useAuthStore((state) => state.setUser)
  const [variant, setVariant] = useState<AuthStatusVariant>('loading')
  const [message, setMessage] = useState('Establishing a secure connection and redirecting you to your workspace.')

  const errorCode = searchParams.get('error')
  const nextPath = searchParams.get('next') || '/app'

  useEffect(() => {
    if (errorCode) {
      setVariant('error')
      setMessage(ssoErrorMessage(errorCode))
      return
    }

    let cancelled = false
    const verify = async () => {
      try {
        const profile = await me()
        if (cancelled) return
        const authUser = authUserFromMeResponse(profile)
        setUser(authUser)
        setSession({ user: authUser })
        setVariant('success')
        setMessage('Sign-in complete. Redirecting to your workspace.')
        window.setTimeout(() => {
          if (!cancelled) navigate(nextPath.startsWith('/') ? nextPath : '/app', { replace: true })
        }, 800)
      } catch {
        if (cancelled) return
        setVariant('error')
        setMessage('We could not verify your session. Please sign in again.')
      }
    }

    void verify()
    return () => {
      cancelled = true
    }
  }, [errorCode, navigate, nextPath, setSession, setUser])

  const title =
    variant === 'loading'
      ? 'Verifying your identity…'
      : variant === 'success'
        ? 'Welcome back'
        : 'Sign-in could not be completed'

  return (
    <AuthShell>
      <AuthStatusScreen
        variant={variant}
        title={title}
        message={message}
        actions={
          variant === 'error' ? (
            <>
              <AuthButton onClick={() => navigate('/login', { replace: true })}>Return to login</AuthButton>
              <Link to="/login" className="block text-center text-label-md text-primary hover:underline">
                Try again
              </Link>
            </>
          ) : null
        }
      />
    </AuthShell>
  )
}
