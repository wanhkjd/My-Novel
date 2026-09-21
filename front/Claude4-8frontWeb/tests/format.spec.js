import { describe, it, expect } from 'vitest'
import { formatWordCount, formatVisit, bookStatusText, formatTime } from '@/utils/format'

describe('format', () => {
  it('formatWordCount', () => {
    expect(formatWordCount(500)).toBe('500字')
    expect(formatWordCount(12000)).toBe('1.2万字')
    expect(formatWordCount(20000)).toBe('2万字')
    expect(formatWordCount(null)).toBe('0字')
  })
  it('formatVisit', () => {
    expect(formatVisit(999)).toBe('999')
    expect(formatVisit(35000)).toBe('3.5万')
    expect(formatVisit(undefined)).toBe('0')
  })
  it('bookStatusText', () => {
    expect(bookStatusText(1)).toBe('已完结')
    expect(bookStatusText(0)).toBe('连载中')
  })
  it('formatTime', () => {
    expect(formatTime('')).toBe('-')
    expect(formatTime('2026-09-21 10:00')).toBe('2026-09-21 10:00')
  })
})
