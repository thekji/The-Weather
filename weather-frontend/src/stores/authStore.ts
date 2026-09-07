import { create } from 'zustand'

const TOKEN_STORAGE_KEY = 'token'
const USER_STORAGE_KEY = 'user'

export type AuthUser = {
    userID: string
    email: string
    name: string
}

export type AuthSession = {
    token: string
    user: AuthUser
}

type AuthStore = {
    token: string | null
    user: AuthUser | null
    setSession: (session: AuthSession) => void
    clearSession: () => void
}

function readStoredUser(): AuthUser | null {
    const storedUser = sessionStorage.getItem(USER_STORAGE_KEY)
    if (!storedUser) return null

    try {
        const user: unknown = JSON.parse(storedUser)
        if (typeof user !== 'object' || user === null) return null

        const fields = user as Record<string, unknown>
        if (typeof fields.userID !== 'string'
            || typeof fields.email !== 'string'
            || typeof fields.name !== 'string') {
            return null
        }

        return {
            userID: fields.userID,
            email: fields.email,
            name: fields.name,
        }
    } catch {
        return null
    }
}

export const useAuthStore = create<AuthStore>((set) => ({
    token: sessionStorage.getItem(TOKEN_STORAGE_KEY),
    user: readStoredUser(),

    setSession: ({ token, user }) => {
        sessionStorage.setItem(TOKEN_STORAGE_KEY, token)
        sessionStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user))
        set({ token, user })
    },

    clearSession: () => {
        sessionStorage.removeItem(TOKEN_STORAGE_KEY)
        sessionStorage.removeItem(USER_STORAGE_KEY)
        set({ token: null, user: null })
    },
}))
