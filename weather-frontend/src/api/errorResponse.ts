export async function getErrorMessage(
    response: Response,
    fallback: string,
): Promise<string> {
    try {
        const body: unknown = await response.json()
        if (typeof body === 'object' && body !== null) {
            const message = (body as Record<string, unknown>).message
            if (typeof message === 'string' && message.length > 0) return message
        }
    } catch {
        // Use the fallback when the server did not return a JSON error.
    }

    return fallback
}
