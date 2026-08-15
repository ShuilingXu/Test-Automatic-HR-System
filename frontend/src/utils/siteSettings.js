export const DEFAULT_SITE_SETTINGS = Object.freeze({
  logoUrl: '',
  siteTitle: '千早爱音',
  siteSubtitle: '让合适的人，找到值得投入的事。了解团队、浏览机会，开启你的下一段职业旅程。',
  footerHtml: '千早爱音 · 人才与组织中心',
})

const limits = { logoUrl: 500, siteTitle: 120, siteSubtitle: 500, footerHtml: 500 }

function cleanText(value, fallback, limit, allowBlank = false) {
  const text = typeof value === 'string' ? value.trim() : ''
  return (text || (allowBlank ? '' : fallback)).slice(0, limit)
}

export function safeBrandAssetUrl(value, fallback = '') {
  const text = typeof value === 'string' ? value.trim() : ''
  if (text.includes('\\')) return fallback
  if (/^\/(?!\/)/.test(text)) return text.slice(0, limits.logoUrl)
  try {
    const url = new URL(text)
    return url.protocol === 'https:' ? url.toString().slice(0, limits.logoUrl) : fallback
  } catch {
    return fallback
  }
}

export function normalizeSiteSettings(value = {}) {
  return {
    logoUrl: safeBrandAssetUrl(value.logoUrl),
    siteTitle: cleanText(value.siteTitle, DEFAULT_SITE_SETTINGS.siteTitle, limits.siteTitle),
    siteSubtitle: cleanText(value.siteSubtitle, DEFAULT_SITE_SETTINGS.siteSubtitle, limits.siteSubtitle),
    footerHtml: cleanText(value.footerHtml, DEFAULT_SITE_SETTINGS.footerHtml, limits.footerHtml, true),
  }
}

export function siteInitials(siteTitle) {
  const characters = Array.from(String(siteTitle || DEFAULT_SITE_SETTINGS.siteTitle).replace(/\s+/g, ''))
  return characters.slice(0, 2).join('').toUpperCase() || 'A'
}

export function applySiteMetadata(value, targetDocument = globalThis.document) {
  if (!targetDocument) return
  const settings = normalizeSiteSettings(value)
  targetDocument.title = `${settings.siteTitle} · 人事管理系统`
  let favicon = targetDocument.querySelector('link[rel~="icon"]')
  if (!favicon) {
    favicon = targetDocument.createElement('link')
    favicon.rel = 'icon'
    targetDocument.head.appendChild(favicon)
  }
  favicon.href = settings.logoUrl || '/favicon.svg'
}
