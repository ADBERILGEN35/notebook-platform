import { expect, type Page } from '@playwright/test'

const FORBIDDEN = [
  /Bearer\s+[A-Za-z0-9._-]{12,}/,
  /eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\./,
  /Authorization:\s*\S+/i,
  /"access_token"\s*:\s*"(?!(\*\*\*masked\*\*\*|\.\.\.))[^"]{20,}"/i,
]

export async function assertNoSecretsVisible(page: Page) {
  const text = await page.locator('body').innerText()
  for (const pattern of FORBIDDEN) {
    expect(text).not.toMatch(pattern)
  }
}
