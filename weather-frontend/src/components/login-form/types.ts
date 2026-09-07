import type { AuthSession } from '../../api/auth'

export type LoginRequest = {
    email: string
    password: string
}

export type LoginResponse = AuthSession

export type LoginErrors = {
    email?: string
    password?: string
}
