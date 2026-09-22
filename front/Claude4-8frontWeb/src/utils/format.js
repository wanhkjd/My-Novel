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

// 去标签 + 解码常见 HTML 实体 + 折叠空白，把富文本简介清成纯文本预览。
// 用于卡片/榜单/轮播等 text-clamp 截断场景，避免 &nbsp;、<br/> 等原样显示。
export function toPlainText(html) {
  if (html == null) return ''
  let s = String(html)
    .replace(/<br\s*\/?>/gi, ' ')   // 换行在截断预览里按空格处理
    .replace(/<[^>]+>/g, '')        // 去掉其余标签
  const named = { nbsp: ' ', lt: '<', gt: '>', amp: '&', quot: '"', apos: "'" }
  s = s.replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, (m, code) => {
    if (code[0] === '#') {
      const n = /^#x/i.test(code) ? parseInt(code.slice(2), 16) : parseInt(code.slice(1), 10)
      return Number.isFinite(n) ? String.fromCodePoint(n) : m
    }
    const key = code.toLowerCase()
    return key in named ? named[key] : m
  })
  return s.replace(/\s+/g, ' ').trim()   // \s 含 &nbsp; 解出的 U+00A0
}

// 渲染 ES 搜索关键字高亮：先整体转义（杜绝 XSS），再仅还原裸 <em> 标签，
// 丢弃后端注入的内联 style，统一改用 .hl 样式类。任何脚本/属性都无法穿透。
export function renderHighlight(raw) {
  if (raw == null) return ''
  const escaped = String(raw)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
  return escaped
    .replace(/&lt;em(?:\s[\s\S]*?)?&gt;/gi, '<em class="hl">')
    .replace(/&lt;\/em&gt;/gi, '</em>')
}
