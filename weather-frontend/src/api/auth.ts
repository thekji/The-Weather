import { useAuthStore } from '../stores/authStore'

export type { AuthSession, AuthUser } from '../stores/authStore'

function isTokenActive(token: string): boolean {
    try {
        const parts = token.split('.')
        if (parts.length !== 3) return false

        const base64 = parts[1]
            .replace(/-/g, '+')
            .replace(/_/g, '/')
            .padEnd(Math.ceil(parts[1].length / 4) * 4, '=')
        const payload: unknown = JSON.parse(atob(base64))

        if (typeof payload !== 'object' || payload === null) return false
        const expiration = (payload as Record<string, unknown>).exp
        return typeof expiration === 'number' && expiration * 1000 > Date.now()
    } catch {
        return false
    }
}

export function getAuthToken(): string | null {
    const token = useAuthStore.getState().token
    if (!token || isTokenActive(token)) return token

    useAuthStore.getState().clearSession()
    return null
}

export function hasAuthSession(): boolean {
    const hasSession = Boolean(getAuthToken() && useAuthStore.getState().user)
    if (!hasSession) useAuthStore.getState().clearSession()
    return hasSession
}

export function getAuthorizationHeaders(): Record<string, string> {
    const token = getAuthToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
}
