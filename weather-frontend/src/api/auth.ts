import { useAuthStore } from '../stores/authStore'

export type { AuthSession } from '../stores/authStore'

export function getTokenExpirationTime(token: string): number | null {
    try {
        const parts = token.split('.')
        if (parts.length !== 3) return null

        const base64 = parts[1]
            .replace(/-/g, '+')
            .replace(/_/g, '/')
            .padEnd(Math.ceil(parts[1].length / 4) * 4, '=')
        const payload: unknown = JSON.parse(atob(base64))

        if (typeof payload !== 'object' || payload === null) return null
        const expiration = (payload as Record<string, unknown>).exp
        return typeof expiration === 'number' && Number.isFinite(expiration)
            ? expiration * 1000
            : null
    } catch {
        return null
    }
}

export function isTokenActive(token: string, now = Date.now()): boolean {
    const expirationTime = getTokenExpirationTime(token)
    return expirationTime !== null && expirationTime > now
}

export function getAuthToken(): string | null {
    const token = useAuthStore.getState().token
    return token && isTokenActive(token) ? token : null
}

export function hasAuthSession(): boolean {
    return Boolean(getAuthToken() && useAuthStore.getState().user)
}

export function getAuthorizationHeaders(): Record<string, string> {
    const token = getAuthToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * Sends a request to an authenticated application endpoint. A 401 invalidates
 * only the session whose token was used for this request, so a late response
 * cannot accidentally sign out a newer login.
 */
export async function authenticatedFetch(
    input: RequestInfo | URL,
    init: RequestInit = {},
): Promise<Response> {
    const storedToken = useAuthStore.getState().token
    const token = getAuthToken()
    const headers = new Headers(init.headers)

    if (storedToken && !token) {
        useAuthStore.getState().clearSession('expired')
    }
    if (token) headers.set('Authorization', `Bearer ${token}`)

    const response = await fetch(input, { ...init, headers })
    if (response.status === 401
        && token
        && useAuthStore.getState().token === token) {
        useAuthStore.getState().clearSession('expired')
    }

    return response
}
