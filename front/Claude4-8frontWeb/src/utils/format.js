export function formatWordCount(n) {
  if (typeof n !== 'number' || isNaN(n)) return '0字'
  if (n >= 10000) return `${parseFloat((n / 10000).toFixed(1))}万字`
  return `${n}字`
}

export function formatVisit(n) {
  if (typeof n !== 'number' || isNaN(n)) return '0'
  if (n >= 10000) return `${parseFloat((n / 10000).toFixed(1))}万`
  return String(n)
}

export function bookStatusText(s) {
  return s === 1 ? '已完结' : '连载中'
}

export function formatTime(v) {
  return v ? String(v) : '-'
}
