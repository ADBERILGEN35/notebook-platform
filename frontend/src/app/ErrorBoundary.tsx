import { Component, type ErrorInfo, type ReactNode } from 'react'
import { useRouteError } from 'react-router-dom'
import { ErrorState } from '../shared/components/ErrorState'

type ErrorBoundaryProps = {
  children: ReactNode
}

type ErrorBoundaryState = {
  error: Error | null
}

/**
 * Top-level boundary that catches render-time errors anywhere below it so a
 * single failing component cannot blank out the whole SPA. Router loader/render
 * errors are handled separately by {@link RouteError} via `errorElement`.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Keep this lightweight: no PII, just enough to correlate in dev tools.
    console.error('Unhandled UI error', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return (
        <div className="mx-auto max-w-xl p-6">
          <ErrorState
            title="Application error"
            message="An unexpected error occurred. Please reload the page."
          />
        </div>
      )
    }
    return this.props.children
  }
}

/** Route-level fallback wired into the router via `errorElement`. */
export function RouteError() {
  const error = useRouteError()
  return (
    <div className="mx-auto max-w-xl p-6">
      <ErrorState title="Page failed to load" error={error} />
    </div>
  )
}
