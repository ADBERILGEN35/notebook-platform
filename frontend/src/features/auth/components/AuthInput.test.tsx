import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AuthInput } from './AuthInput'

describe('AuthInput', () => {
  it('associates label with input for accessibility', () => {
    render(<AuthInput label="Work email" name="email" type="email" />)
    const input = screen.getByLabelText('Work email')
    expect(input).toHaveAttribute('name', 'email')
    expect(input).toHaveAttribute('type', 'email')
  })
})
