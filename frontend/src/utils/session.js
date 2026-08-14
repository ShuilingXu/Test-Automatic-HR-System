const SESSION_USER_KEY = 'session-user'
const TOKEN_KEY = 'demo-token'

function isSessionUser(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
}

export function readSessionToken() {
  return window.localStorage.getItem(TOKEN_KEY)
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
  writeSessionUser(user)
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_USER_KEY)
  window.localStorage.removeItem(TOKEN_KEY)
}
