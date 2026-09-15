import { httpHelper } from '../../../utils/httpHelper'
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
    return httpHelper.post<LoginResponse>('/api/auth/login', request, {
        fallbackError: 'Login could not be completed.',
        invalidResponseMessage: 'The login service returned an invalid response.',
        statusMessages: {
            401: 'Email or password is invalid.',
        },
        validate: isLoginResponse,
    })
}
