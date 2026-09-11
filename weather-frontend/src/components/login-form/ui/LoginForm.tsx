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
            <form className="mt-[30px] grid gap-[18px]" onSubmit={handleSubmit} noValidate>
                <div className="grid gap-[7px]">
                    <label className="text-[0.8rem] font-extrabold text-[var(--muted-strong)]" htmlFor="login-email">Email</label>
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
                        className="glass-inset min-h-[49px] w-full rounded-[13px] border border-white/60 px-3.5 text-[var(--ink)] outline-none transition focus:border-[var(--accent)] focus:shadow-[0_0_0_3px_rgba(21,150,245,0.16)] aria-invalid:border-[#d66a73] disabled:opacity-60 motion-reduce:transition-none"
                    />
                    {errors.email && (
                        <p id="login-email-error" className="m-0 text-[0.76rem] font-semibold text-[#a93844]">
                            {errors.email}
                        </p>
                    )}
                </div>

                <div className="grid gap-[7px]">
                    <label className="text-[0.8rem] font-extrabold text-[var(--muted-strong)]" htmlFor="login-password">Password</label>
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
                        className="glass-inset min-h-[49px] w-full rounded-[13px] border border-white/60 px-3.5 text-[var(--ink)] outline-none transition focus:border-[var(--accent)] focus:shadow-[0_0_0_3px_rgba(21,150,245,0.16)] aria-invalid:border-[#d66a73] disabled:opacity-60 motion-reduce:transition-none"
                    />
                    {errors.password && (
                        <p id="login-password-error" className="m-0 text-[0.76rem] font-semibold text-[#a93844]">
                            {errors.password}
                        </p>
                    )}
                </div>

                {requestError && (
                    <p className="m-0 rounded-[11px] bg-[#ffeff1cc] px-[13px] py-[11px] text-[0.8rem] leading-[1.4] font-semibold text-[#9f3340] backdrop-blur-xl" role="alert">{requestError}</p>
                )}

                <button className="min-h-[50px] cursor-pointer rounded-[13px] border border-[var(--active-border)] [background:var(--action-background)] px-5 font-extrabold text-[var(--action-ink)] shadow-[0_12px_28px_rgba(21,50,84,0.25)] transition-[background,transform] hover:not-disabled:[background:var(--action-hover-background)] active:not-disabled:scale-[0.96] disabled:cursor-wait disabled:opacity-60 motion-reduce:transition-none" type="submit" disabled={isLoading}>
                    {isLoading ? 'Logging in…' : 'Login'}
                </button>
            </form>

            <p className="mt-6 mb-0 text-center text-[0.84rem] text-[var(--muted)]">
                Do not have an account? <Link className="font-extrabold text-[var(--accent)] no-underline hover:underline" to="/register">Register</Link>
            </p>
        </>
    )
}
