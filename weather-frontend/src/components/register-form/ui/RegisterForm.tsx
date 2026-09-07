import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { register } from '../service/registerService'
import type { RegisterErrors } from '../types'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function RegisterForm() {
    const navigate = useNavigate()
    const [email, setEmail] = useState('')
    const [name, setName] = useState('')
    const [password, setPassword] = useState('')
    const [errors, setErrors] = useState<RegisterErrors>({})
    const [requestError, setRequestError] = useState('')
    const [isLoading, setIsLoading] = useState(false)

    function validate(): RegisterErrors {
        const nextErrors: RegisterErrors = {}
        const normalizedEmail = email.trim()

        if (!normalizedEmail) {
            nextErrors.email = 'Email is required.'
        } else if (!EMAIL_PATTERN.test(normalizedEmail)) {
            nextErrors.email = 'Enter a valid email address.'
        }
        if (!name.trim()) nextErrors.name = 'Name is required.'
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
            await register({
                email: email.trim().toLowerCase(),
                name: name.trim(),
                password,
            })
            navigate('/login', { replace: true })
        } catch (error) {
            setRequestError(error instanceof Error
                ? error.message
                : 'Registration could not be completed.')
        } finally {
            setIsLoading(false)
        }
    }

    return (
        <>
            <form className="auth-form" onSubmit={handleSubmit} noValidate>
                <div className="auth-field">
                    <label htmlFor="register-email">Email</label>
                    <input
                        id="register-email"
                        name="email"
                        type="email"
                        autoComplete="email"
                        value={email}
                        onChange={(event) => setEmail(event.target.value)}
                        aria-invalid={Boolean(errors.email)}
                        aria-describedby={errors.email ? 'register-email-error' : undefined}
                        disabled={isLoading}
                    />
                    {errors.email && (
                        <p id="register-email-error" className="auth-field-error">
                            {errors.email}
                        </p>
                    )}
                </div>

                <div className="auth-field">
                    <label htmlFor="register-name">Name</label>
                    <input
                        id="register-name"
                        name="name"
                        type="text"
                        autoComplete="name"
                        value={name}
                        onChange={(event) => setName(event.target.value)}
                        aria-invalid={Boolean(errors.name)}
                        aria-describedby={errors.name ? 'register-name-error' : undefined}
                        disabled={isLoading}
                    />
                    {errors.name && (
                        <p id="register-name-error" className="auth-field-error">
                            {errors.name}
                        </p>
                    )}
                </div>

                <div className="auth-field">
                    <label htmlFor="register-password">Password</label>
                    <input
                        id="register-password"
                        name="password"
                        type="password"
                        autoComplete="new-password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        aria-invalid={Boolean(errors.password)}
                        aria-describedby={
                            errors.password ? 'register-password-error' : undefined
                        }
                        disabled={isLoading}
                    />
                    {errors.password && (
                        <p id="register-password-error" className="auth-field-error">
                            {errors.password}
                        </p>
                    )}
                </div>

                {requestError && (
                    <p className="auth-request-error" role="alert">{requestError}</p>
                )}

                <button className="auth-submit" type="submit" disabled={isLoading}>
                    {isLoading ? 'Creating account…' : 'Register'}
                </button>
            </form>

            <p className="auth-switch">
                Already have an account? <Link to="/login">Login</Link>
            </p>
        </>
    )
}
