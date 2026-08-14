const SESSION_USER_KEY = 'session-user'
const TOKEN_KEY = 'autohr-access-token'
const LEGACY_TOKEN_KEY = 'demo-token'

function isSessionUser(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
}

export function readSessionToken() {
  const token = window.localStorage.getItem(TOKEN_KEY)
  if (token) {
    window.localStorage.removeItem(LEGACY_TOKEN_KEY)
    return token
  }
  const legacyToken = window.localStorage.getItem(LEGACY_TOKEN_KEY)
  if (!legacyToken) return null
  try {
    window.localStorage.setItem(TOKEN_KEY, legacyToken)
    window.localStorage.removeItem(LEGACY_TOKEN_KEY)
  } catch {
    // Keep the existing login usable when browser storage cannot be updated.
  }
  return legacyToken
}

export function readSessionUser() {
  const raw = window.localStorage.getItem(SESSION_USER_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw)
    if (!isSessionUser(session)) {
      throw new Error('Invalid session')
    }
    return session
  } catch {
    clearSession()
    return null
  }
}

export function writeSessionUser(user) {
  if (!isSessionUser(user)) {
    window.localStorage.removeItem(SESSION_USER_KEY)
    return
  }
  window.localStorage.setItem(SESSION_USER_KEY, JSON.stringify(user))
}

export function writeSession(token, user) {
  if (typeof token !== 'string' || !token.trim() || !isSessionUser(user)) {
    clearSession()
    return
  }
  window.localStorage.setItem(TOKEN_KEY, token)
  window.localStorage.removeItem(LEGACY_TOKEN_KEY)
  writeSessionUser(user)
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_USER_KEY)
  window.localStorage.removeItem(TOKEN_KEY)
  window.localStorage.removeItem(LEGACY_TOKEN_KEY)
}
