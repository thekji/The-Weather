import { getErrorMessage } from '../../../api/errorResponse'
import type { LoginRequest, LoginResponse } from '../types'

function isLoginResponse(value: unknown): value is LoginResponse {
    if (typeof value !== 'object' || value === null) return false

    const response = value as Record<string, unknown>
    if (typeof response.token !== 'string' || response.token.length === 0) return false
    if (typeof response.user !== 'object' || response.user === null) return false

    const user = response.user as Record<string, unknown>
    return typeof user.userID === 'string'
        && typeof user.email === 'string'
        && typeof user.name === 'string'
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
    const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    })

    if (!response.ok) {
        if (response.status === 401) {
            throw new Error('Email or password is invalid.')
        }
        throw new Error(await getErrorMessage(response, 'Login could not be completed.'))
    }

    const result: unknown = await response.json()
    if (!isLoginResponse(result)) {
        throw new Error('The login service returned an invalid response.')
    }

    return result
}
