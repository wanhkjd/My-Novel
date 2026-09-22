import { describe, it, expect } from 'vitest'
import { formatWordCount, formatVisit, bookStatusText, formatTime, toPlainText, renderHighlight } from '@/utils/format'

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

  it('toPlainText 去标签+解实体+折叠空白', () => {
    const raw = '&nbsp;&nbsp;&nbsp;&nbsp;她是冉府的嫡长女<br/><br/>&nbsp;&nbsp;多年后回府为母报仇'
    expect(toPlainText(raw)).toBe('她是冉府的嫡长女 多年后回府为母报仇')
    expect(toPlainText('数字实体&#65;&#x42;')).toBe('数字实体AB')
    expect(toPlainText(null)).toBe('')
    expect(toPlainText('')).toBe('')
  })

  it('renderHighlight 渲染 <em> 高亮', () => {
    const raw = "我爱<em style='color:red'>斗破</em>苍穹"
    expect(renderHighlight(raw)).toBe('我爱<em class="hl">斗破</em>苍穹')
    // 无高亮时原样返回（转义后无 em）
    expect(renderHighlight('普通书名')).toBe('普通书名')
    expect(renderHighlight(null)).toBe('')
  })

  it('renderHighlight 防 XSS：脚本/属性被转义，不可穿透', () => {
    const evil = "<img src=x onerror=alert(1)><script>alert(2)</script><em>命中</em>"
    const out = renderHighlight(evil)
    // 关键不变量：没有任何"未转义"的活动标签。onerror 作为纯文本残留无害（< 已被转义）。
    expect(out).not.toMatch(/<img/i)
    expect(out).not.toMatch(/<script/i)
    expect(out).not.toMatch(/<[^>]*onerror/i)   // 不存在带 onerror 的真实标签
    expect(out).toContain('&lt;img')            // 危险标签被转义成文本
    expect(out).toContain('&lt;script&gt;')
    expect(out).toContain('<em class="hl">命中</em>')  // 仅裸 em 被还原
  })
})
