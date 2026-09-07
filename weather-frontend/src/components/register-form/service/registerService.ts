import { getErrorMessage } from '../../../api/errorResponse'
import type { RegisterRequest } from '../types'

export async function register(request: RegisterRequest): Promise<void> {
    const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    })

    if (!response.ok) {
        if (response.status === 409) {
            throw new Error('The email already exists.')
        }
        throw new Error(await getErrorMessage(response, 'Registration could not be completed.'))
    }
}
