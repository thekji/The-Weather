import { authenticatedFetch } from '../api/auth'

export interface HttpError extends Error {
    status: number
    data?: unknown
}

export type ResponseValidator<T> = (value: unknown) => value is T

export type HttpRequestOptions<T> = {
    authenticated?: boolean
    fallbackError?: string
    headers?: HeadersInit
    invalidResponseMessage?: string
    signal?: AbortSignal
    statusMessages?: Partial<Record<number, string>>
    validate?: ResponseValidator<T>
}

async function readResponseBody(response: Response): Promise<unknown> {
    const contentType = response.headers.get('content-type') ?? ''

    try {
        return contentType.includes('json')
            ? await response.json()
            : await response.text()
    } catch {
        return null
    }
}

function messageFromBody(body: unknown): string | null {
    if (typeof body === 'object' && body !== null) {
        const message = (body as Record<string, unknown>).message
        if (typeof message === 'string' && message.trim()) return message
    }

    return typeof body === 'string' && body.trim() ? body : null
}

class HttpHelper {
    async get<T>(endpoint: string, options?: HttpRequestOptions<T>): Promise<T> {
        return this.request<T>(endpoint, 'GET', undefined, options)
    }

    async post<T>(
        endpoint: string,
        data?: unknown,
        options?: HttpRequestOptions<T>,
    ): Promise<T> {
        return this.request<T>(endpoint, 'POST', data, options)
    }

    async put<T>(
        endpoint: string,
        data?: unknown,
        options?: HttpRequestOptions<T>,
    ): Promise<T> {
        return this.request<T>(endpoint, 'PUT', data, options)
    }

    async patch<T>(
        endpoint: string,
        data?: unknown,
        options?: HttpRequestOptions<T>,
    ): Promise<T> {
        return this.request<T>(endpoint, 'PATCH', data, options)
    }

    async delete<T = void>(
        endpoint: string,
        options?: HttpRequestOptions<T>,
    ): Promise<T> {
        return this.request<T>(endpoint, 'DELETE', undefined, options)
    }

    private async request<T>(
        endpoint: string,
        method: string,
        data?: unknown,
        options: HttpRequestOptions<T> = {},
    ): Promise<T> {
        const headers = new Headers(options.headers)
        if (!headers.has('Accept')) headers.set('Accept', 'application/json')

        let body: BodyInit | undefined
        if (data instanceof FormData) {
            body = data
        } else if (data !== undefined) {
            headers.set('Content-Type', 'application/json')
            body = JSON.stringify(data)
        }

        const requestInit: RequestInit = {
            method,
            headers,
            body,
            signal: options.signal,
        }
        const response = options.authenticated
            ? await authenticatedFetch(endpoint, requestInit)
            : await fetch(endpoint, requestInit)

        if (!response.ok) {
            const errorBody = await readResponseBody(response)
            const message = options.statusMessages?.[response.status]
                ?? messageFromBody(errorBody)
                ?? options.fallbackError
                ?? `Request failed (${response.status}).`
            const error = new Error(message) as HttpError
            error.status = response.status
            error.data = errorBody
            throw error
        }

        if (response.status === 204) return undefined as T

        let result: unknown
        try {
            result = await response.json()
        } catch {
            throw new Error(
                options.invalidResponseMessage
                ?? options.fallbackError
                ?? 'The server returned an invalid response.',
            )
        }

        if (options.validate && !options.validate(result)) {
            throw new Error(
                options.invalidResponseMessage
                ?? 'The server returned an invalid response.',
            )
        }

        return result as T
    }
}

export const httpHelper = new HttpHelper()

export { HttpHelper }
