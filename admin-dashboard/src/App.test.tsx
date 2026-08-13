import { describe, expect, it } from 'vitest'
import { dateText } from './App'

describe('dateText', () => {
  it('shows an em dash for missing dates', () => {
    expect(dateText(null)).toBe('—')
  })

  it('supports Firestore timestamp-like values', () => {
    expect(dateText({ toDate: () => new Date('2026-07-15T00:00:00Z') } as never)).not.toBe('—')
  })
})
