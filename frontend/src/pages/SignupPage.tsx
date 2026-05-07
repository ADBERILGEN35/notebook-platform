import { useMutation } from '@tanstack/react-query'
import { useNavigate, Link } from 'react-router-dom'
import { useState } from 'react'
import { signup, signupSchema, me } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import { Card } from '../shared/components/Card'
import { Input } from '../shared/components/Input'
import { Button } from '../shared/components/Button'
import { ErrorAlert } from '../shared/components/ErrorAlert'

export function SignupPage() {
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const setUser = useAuthStore((state) => state.setUser)

  const mutation = useMutation({
    mutationFn: signup,
    onSuccess: async (data) => {
      setSession({
        accessToken: data.accessToken ?? null,
        refreshToken: data.refreshToken ?? null,
        user: data.user,
      })
      try {
        const m = await me()
        setUser({
          id: m.userId,
          email: m.email,
          name: m.name,
          avatarUrl: m.avatarUrl ?? null,
          status: data.user.status,
          roles: m.roles ?? [],
        })
      } catch {
        // ignore enrichment failure
      }
      navigate('/app')
    },
  })

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    const parsed = signupSchema.safeParse({ name, email, password, avatarUrl: '' })
    if (!parsed.success) return
    mutation.mutate(parsed.data)
  }

  return (
    <div className="grid min-h-screen place-items-center p-4">
      <Card>
        <h1 className="mb-4 text-xl font-semibold">Create account</h1>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Input placeholder="Name" value={name} onChange={(event) => setName(event.target.value)} />
          <Input placeholder="Email" value={email} onChange={(event) => setEmail(event.target.value)} />
          <Input
            placeholder="Password (min 10 chars)"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {mutation.isError ? <ErrorAlert error={mutation.error} /> : null}
          <Button className="w-full bg-primary-600 text-white hover:bg-primary-700" type="submit">
            Sign up
          </Button>
        </form>
        <p className="mt-3 text-sm text-slate-600">
          Already registered? <Link to="/login">Login</Link>
        </p>
      </Card>
    </div>
  )
}

