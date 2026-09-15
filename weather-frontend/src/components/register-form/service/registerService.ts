import { httpHelper } from '../../../utils/httpHelper'
import type { RegisterRequest } from '../types'

export async function register(request: RegisterRequest): Promise<void> {
    await httpHelper.post<unknown>('/api/auth/register', request, {
        fallbackError: 'Registration could not be completed.',
        statusMessages: {
            409: 'The email already exists.',
        },
    })
}
