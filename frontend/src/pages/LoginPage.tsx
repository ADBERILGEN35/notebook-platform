import { useMutation } from '@tanstack/react-query'
import { useNavigate, Link } from 'react-router-dom'
import { useState } from 'react'
import { login, loginSchema } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import { Card } from '../shared/components/Card'
import { Input } from '../shared/components/Input'
import { Button } from '../shared/components/Button'
import { ErrorAlert } from '../shared/components/ErrorAlert'

export function LoginPage() {
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user })
      navigate('/app')
    },
  })

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    const parsed = loginSchema.safeParse({ email, password })
    if (!parsed.success) return
    mutation.mutate(parsed.data)
  }

  return (
    <div className="grid min-h-screen place-items-center p-4">
      <Card>
        <h1 className="mb-4 text-xl font-semibold">Login</h1>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Input placeholder="Email" value={email} onChange={(event) => setEmail(event.target.value)} />
          <Input
            placeholder="Password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {mutation.isError ? <ErrorAlert error={mutation.error} /> : null}
          <Button className="w-full bg-primary-600 text-white hover:bg-primary-700" type="submit">
            Sign in
          </Button>
        </form>
        <p className="mt-3 text-sm text-slate-600">
          No account? <Link to="/signup">Create one</Link>
        </p>
      </Card>
    </div>
  )
}

