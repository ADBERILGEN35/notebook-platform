import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SkipToMain } from '../shared/components/SkipToMain'
import { ResponsiveTableShell } from '../shared/components/ResponsiveTableShell'
import { Modal } from '../shared/components/Modal'

describe('Faz 145 accessibility baseline', () => {
  it('SkipToMain links to main content', () => {
    render(
      <>
        <SkipToMain targetId="main-content" />
        <main id="main-content">Hello</main>
      </>,
    )
    const link = screen.getByRole('link', { name: /Skip to main content/i })
    expect(link).toHaveAttribute('href', '#main-content')
  })

  it('ResponsiveTableShell exposes region label', () => {
    render(
      <ResponsiveTableShell label="Test table" testId="table-region">
        <table>
          <tbody>
            <tr>
              <td>cell</td>
            </tr>
          </tbody>
        </table>
      </ResponsiveTableShell>,
    )
    expect(screen.getByRole('region', { name: 'Test table' })).toBeTruthy()
  })

  it('Modal has dialog role and labelled close control', () => {
    render(
      <Modal open title="Confirm" onClose={() => {}}>
        <p>Body</p>
      </Modal>,
    )
    expect(screen.getByRole('dialog', { name: 'Confirm' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Close dialog' })).toBeTruthy()
  })
})
