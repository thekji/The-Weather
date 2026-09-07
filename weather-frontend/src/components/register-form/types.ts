export type RegisterRequest = {
    email: string
    name: string
    password: string
}

export type RegisterErrors = {
    email?: string
    name?: string
    password?: string
}
