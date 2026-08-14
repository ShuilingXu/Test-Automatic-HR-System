const SESSION_USER_KEY = 'session-user'
const TOKEN_KEY = 'demo-token'

export function readSessionUser() {
  const raw = window.localStorage.getItem(SESSION_USER_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw)
    if (!session || typeof session !== 'object' || Array.isArray(session)) {
      throw new Error('Invalid session')
    }
    return session
  } catch {
    clearSession()
    return null
  }
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_USER_KEY)
  window.localStorage.removeItem(TOKEN_KEY)
}
