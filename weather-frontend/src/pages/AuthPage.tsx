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
        <main className="time-background relative isolate grid min-h-svh place-items-center px-5 py-8 max-[760px]:px-[15px] max-[760px]:py-5">
            <section className="glass-surface-strong relative z-[1] w-[min(100%,450px)] overflow-hidden rounded-[28px] border border-white/60 bg-white/38 p-[clamp(28px,6vw,46px)] shadow-[0_24px_70px_rgba(21,50,84,0.18),inset_0_1px_0_rgba(255,255,255,0.78)] backdrop-blur-[32px] backdrop-saturate-175 before:pointer-events-none before:absolute before:inset-0 before:bg-[linear-gradient(135deg,rgba(255,255,255,0.35),transparent_45%,rgba(105,174,224,0.12))] before:content-[''] max-[760px]:rounded-[23px] max-[760px]:px-[22px] max-[760px]:py-7" aria-labelledby="auth-title">
                <Link className="relative z-[1] inline-flex items-center gap-2.5 text-[1.02rem] font-extrabold text-[var(--ink)] no-underline focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#1596f566]" to="/" aria-label="TheWeather home">
                    <span className="h-[38px] w-[38px]"><WeatherBrandIcon /></span>
                    <span>TheWeather</span>
                </Link>

                <header className="relative z-[1] mt-[38px]">
                    <p className="m-0 mb-[9px] text-xs font-extrabold tracking-[0.14em] text-[var(--eyebrow)] uppercase">{eyebrow}</p>
                    <h1 className="m-0 text-[clamp(2.3rem,8vw,3.35rem)] leading-none font-extrabold tracking-[0.01em] text-[var(--ink)]" id="auth-title">{title}</h1>
                    <p className="mt-3.5 mb-0 text-[0.92rem] leading-[1.55] text-[var(--muted)]">{description}</p>
                </header>

                <div className="relative z-[1]">{children}</div>
            </section>
        </main>
    )
}
