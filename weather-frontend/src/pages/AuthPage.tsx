import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'
import { WeatherBrandIcon } from '../components/WeatherBrandIcon'

type AuthPageProps = {
    eyebrow: string
    title: string
    description: string
    children: ReactNode
}

export function AuthPage({ eyebrow, title, description, children }: AuthPageProps) {
    return (
        <main className="auth-page">
            <section className="auth-card" aria-labelledby="auth-title">
                <Link className="auth-brand" to="/" aria-label="TheWeather home">
                    <span className="auth-brand-mark"><WeatherBrandIcon /></span>
                    <span>TheWeather</span>
                </Link>

                <header className="auth-header">
                    <p className="eyebrow">{eyebrow}</p>
                    <h1 id="auth-title">{title}</h1>
                    <p>{description}</p>
                </header>

                {children}
            </section>
        </main>
    )
}
