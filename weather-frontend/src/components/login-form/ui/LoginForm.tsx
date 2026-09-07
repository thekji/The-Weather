import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../../stores/authStore'
import { login } from '../service/loginService'
import type { LoginErrors } from '../types'

export function LoginForm() {
    const navigate = useNavigate()
    const setSession = useAuthStore((state) => state.setSession)
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [errors, setErrors] = useState<LoginErrors>({})
    const [requestError, setRequestError] = useState('')
    const [isLoading, setIsLoading] = useState(false)

    function validate(): LoginErrors {
        const nextErrors: LoginErrors = {}
        if (!email.trim()) nextErrors.email = 'Email is required.'
        if (!password) nextErrors.password = 'Password is required.'
        return nextErrors
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()

        const nextErrors = validate()
        setErrors(nextErrors)
        setRequestError('')
        if (Object.keys(nextErrors).length > 0) return

        setIsLoading(true)
        try {
            const response = await login({
                email: email.trim().toLowerCase(),
                password,
            })
            setSession(response)
            navigate('/map', { replace: true })
        } catch (error) {
            setRequestError(error instanceof Error
                ? error.message
                : 'Login could not be completed.')
        } finally {
            setIsLoading(false)
        }
    }

    return (
        <>
            <form className="auth-form" onSubmit={handleSubmit} noValidate>
                <div className="auth-field">
                    <label htmlFor="login-email">Email</label>
                    <input
                        id="login-email"
                        name="email"
                        type="email"
                        autoComplete="email"
                        value={email}
                        onChange={(event) => setEmail(event.target.value)}
                        aria-invalid={Boolean(errors.email)}
                        aria-describedby={errors.email ? 'login-email-error' : undefined}
                        disabled={isLoading}
                    />
                    {errors.email && (
                        <p id="login-email-error" className="auth-field-error">
                            {errors.email}
                        </p>
                    )}
                </div>

                <div className="auth-field">
                    <label htmlFor="login-password">Password</label>
                    <input
                        id="login-password"
                        name="password"
                        type="password"
                        autoComplete="current-password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        aria-invalid={Boolean(errors.password)}
                        aria-describedby={errors.password ? 'login-password-error' : undefined}
                        disabled={isLoading}
                    />
                    {errors.password && (
                        <p id="login-password-error" className="auth-field-error">
                            {errors.password}
                        </p>
                    )}
                </div>

                {requestError && (
                    <p className="auth-request-error" role="alert">{requestError}</p>
                )}

                <button className="auth-submit" type="submit" disabled={isLoading}>
                    {isLoading ? 'Logging in…' : 'Login'}
                </button>
            </form>

            <p className="auth-switch">
                Do not have an account? <Link to="/register">Register</Link>
            </p>
        </>
    )
}
