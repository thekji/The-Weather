import { Laptop, LogOut, Mail, MessageSquare, Moon, Star, Sun, UserRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getStarredLocations } from '../api/stars'
import { PageFooterNote } from '../components/PageFooterNote'
import { useAuthStore } from '../stores/authStore'
import { useTheme, type ThemePreference } from '../theme/theme'

const themeOptions: ReadonlyArray<{
    value: ThemePreference
    label: string
    icon: typeof Sun
}> = [
    { value: 'auto', label: 'Auto', icon: Laptop },
    { value: 'day', label: 'Day', icon: Sun },
    { value: 'night', label: 'Night', icon: Moon },
]

function initials(name: string): string {
    return name.trim().split(/\s+/).slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join('') || 'U'
}

export function AccountPage() {
    const navigate = useNavigate()
    const user = useAuthStore((state) => state.user)
    const clearSession = useAuthStore((state) => state.clearSession)
    const { preference, resolvedTheme, setPreference } = useTheme()
    const [starredLocationCount, setStarredLocationCount] = useState<number | null>(null)
    const [starredLocationCountError, setStarredLocationCountError] = useState(false)

    useEffect(() => {
        const controller = new AbortController()
        void getStarredLocations(controller.signal)
            .then((locations) => {
                if (!controller.signal.aborted) setStarredLocationCount(locations.length)
            })
            .catch(() => {
                if (!controller.signal.aborted) setStarredLocationCountError(true)
            })
        return () => controller.abort()
    }, [])

    function handleLogout() {
        clearSession()
        navigate('/login', { replace: true })
    }

    if (!user) return null

    const panelClassName = 'scroll-glass-card min-w-0 rounded-[20px] border [background:var(--account-panel-background)] p-[30px] [box-shadow:var(--glass-panel-shadow)] max-[1100px]:p-[26px] max-[760px]:p-5 max-[430px]:rounded-2xl max-[430px]:p-[17px] max-[350px]:p-3.5'
    const statClassName = 'scroll-glass-card flex min-h-[94px] items-center gap-[18px] rounded-2xl border [background:var(--glass-grid-background)] px-[18px] py-4 max-[350px]:gap-3 max-[350px]:px-3'
    const detailTermClassName = 'flex items-center gap-2.5 text-[0.78rem] font-extrabold text-[var(--tertiary-ink)] [&_svg]:size-[19px] [&_svg]:stroke-2 [&_svg]:text-[var(--accent)]'

    return (
        <section className="relative z-[1] mx-auto flex w-full max-w-[1600px] flex-col" aria-labelledby="account-title">
            <header className="mb-[clamp(20px,3.5vh,34px)] shrink-0">
                <p className="mt-0 mb-[9px] w-fit text-xs font-extrabold tracking-[0.14em] text-[var(--eyebrow)] uppercase">Profile</p>
                <h1 className="m-0 w-fit text-[clamp(2.25rem,4vw,3.75rem)] leading-[0.98] font-extrabold tracking-[0.01em] text-[var(--ink)] [@media(max-height:720px)_and_(min-width:761px)]:text-[clamp(2.25rem,6vh,3.35rem)] max-[760px]:text-[clamp(2.2rem,11vw,3.25rem)]" id="account-title">Account</h1>
                <p className="mt-[15px] mb-0 text-base leading-[1.6] text-[var(--muted)]">Your basic account information.</p>
            </header>

            <article className="grid grid-cols-[minmax(0,1.2fr)_minmax(340px,0.8fr)] gap-5 bg-transparent pt-5 max-[1100px]:grid-cols-1 max-[430px]:pt-2.5">
                <section className={panelClassName} aria-labelledby="basic-information-title">
                    <div className="flex items-center gap-5 border-b border-[#c7d1db9e] pb-[22px] max-[430px]:gap-3.5">
                        <div className="grid size-[72px] shrink-0 place-items-center rounded-full border border-[#1596f521] bg-white/70 text-[1.35rem] font-extrabold text-[var(--accent)] max-[430px]:size-[60px]" aria-hidden="true">{initials(user.name)}</div>
                        <div className="min-w-0">
                            <h2 className="m-0 text-[1.18rem] text-[var(--ink)]">{user.name}</h2>
                            <p className="mt-[5px] mb-0 [overflow-wrap:anywhere] text-sm text-[var(--muted)]">{user.email}</p>
                        </div>
                    </div>

                    <div className="mt-7">
                        <h2 className="m-0 text-[1.08rem] text-[var(--ink)]" id="basic-information-title">Basic information</h2>
                        <p className="mt-[5px] mb-0 text-[0.78rem] leading-[1.4] text-[var(--muted)]">Your account details.</p>
                    </div>

                    <dl className="mt-[22px] mb-0 grid gap-0 rounded-[14px] border border-white/50 bg-[var(--glass-inset-background)] px-[18px]">
                        <div className="grid min-h-[68px] grid-cols-[minmax(150px,0.52fr)_minmax(0,1fr)] items-center gap-4 px-0.5 py-2.5 max-[430px]:grid-cols-1 max-[430px]:gap-1">
                            <dt className={detailTermClassName}><UserRound aria-hidden="true" />Name</dt>
                            <dd className="m-0 min-w-0 [overflow-wrap:anywhere] text-[0.84rem] font-bold text-[var(--ink)]">{user.name}</dd>
                        </div>
                        <div className="grid min-h-[68px] grid-cols-[minmax(150px,0.52fr)_minmax(0,1fr)] items-center gap-4 border-t border-[#c7d1db94] px-0.5 py-2.5 max-[430px]:grid-cols-1 max-[430px]:gap-1">
                            <dt className={detailTermClassName}><Mail aria-hidden="true" />Email address</dt>
                            <dd className="m-0 min-w-0 [overflow-wrap:anywhere] text-[0.84rem] font-bold text-[var(--ink)]">{user.email}</dd>
                        </div>
                    </dl>
                </section>

                <section className={`${panelClassName} flex flex-col max-[1100px]:grid max-[1100px]:grid-cols-2 max-[1100px]:gap-3.5 max-[760px]:flex`} aria-labelledby="account-overview-title">
                    <div className="mt-0 mb-[22px] max-[1100px]:col-span-full">
                        <h2 className="m-0 text-[1.08rem] text-[var(--ink)]" id="account-overview-title">Account overview</h2>
                        <p className="mt-[5px] mb-0 text-[0.78rem] leading-[1.4] text-[var(--muted)]">Your activity on the weather app.</p>
                    </div>

                    <div className="grid gap-3.5 max-[1100px]:contents max-[760px]:grid">
                        <article className={statClassName}>
                            <span className="grid size-[34px] shrink-0 place-items-center text-[#ffc21c] [&_svg]:size-[30px] [&_svg]:fill-current [&_svg]:stroke-current [&_svg]:stroke-[1.8] [&_svg]:[stroke-linecap:round] [&_svg]:[stroke-linejoin:round]"><Star aria-hidden="true" /></span>
                            <div className="min-w-0">
                                <h3 className="m-0 text-[0.84rem] text-[var(--secondary-ink)]">Starred locations</h3>
                                <p className="mt-1 mb-0 text-[0.76rem] text-[var(--muted)]">
                                    <strong className="mr-[7px] text-[1.3rem] text-[var(--ink)]">{starredLocationCountError ? '—' : starredLocationCount ?? '…'}</strong>{' '}
                                    {starredLocationCountError ? 'Saved count unavailable' : starredLocationCount === 1 ? 'Location saved' : 'Locations saved'}
                                </p>
                            </div>
                        </article>

                        <article className={statClassName}>
                            <span className="grid size-[34px] shrink-0 place-items-center text-[#596ee8] [&_svg]:size-[30px] [&_svg]:fill-[#596ee81f] [&_svg]:stroke-current [&_svg]:stroke-[1.8] [&_svg]:[stroke-linecap:round] [&_svg]:[stroke-linejoin:round]"><MessageSquare aria-hidden="true" /></span>
                            <div className="min-w-0">
                                <h3 className="m-0 text-[0.84rem] text-[var(--secondary-ink)]">Posts</h3>
                                <p className="mt-1 mb-0 text-[0.76rem] text-[var(--muted)]"><strong className="mr-[7px] text-[1.3rem] text-[var(--ink)]">0</strong> Posts shared</p>
                            </div>
                        </article>
                    </div>

                    <section className="glass-inset mt-4 rounded-2xl border border-white/50 p-1.5 max-[1100px]:col-span-full max-[1100px]:mt-0" aria-labelledby="appearance-title">
                        <div className="flex items-center justify-between gap-3 px-3 py-2">
                            <div>
                                <h3 className="m-0 text-[0.84rem] text-[var(--secondary-ink)]" id="appearance-title">Appearance</h3>
                                <p className="mt-1 mb-0 text-[0.7rem] text-[var(--muted)]">Currently using {resolvedTheme} colors.</p>
                            </div>
                        </div>
                        <div className="grid grid-cols-3 gap-1.5" role="group" aria-label="Color theme">
                            {themeOptions.map((option) => {
                                const Icon = option.icon
                                const selected = preference === option.value
                                return (
                                    <button
                                        type="button"
                                        className={`inline-flex min-h-10 cursor-pointer items-center justify-center gap-1.5 rounded-xl border px-2 text-[0.76rem] font-bold transition-[transform,background-color,border-color,color] duration-150 active:scale-[0.95] motion-reduce:transition-none ${selected ? 'border-[var(--active-border)] bg-[var(--active-surface)] text-[var(--ink)] shadow-[inset_0_1px_0_rgba(255,255,255,0.32)]' : 'border-transparent bg-transparent text-[var(--muted)] hover:bg-[var(--active-surface)] hover:text-[var(--ink)]'}`}
                                        aria-pressed={selected}
                                        onClick={() => setPreference(option.value)}
                                        key={option.value}
                                    >
                                        <Icon className="size-4" aria-hidden="true" />
                                        {option.label}
                                    </button>
                                )
                            })}
                        </div>
                    </section>

                    <div className="mt-4 flex items-center justify-between gap-4 rounded-2xl border border-white/50 [background:var(--glass-grid-background)] px-[18px] py-[15px] max-[1100px]:col-span-full max-[1100px]:mt-0 max-[430px]:flex-col max-[430px]:items-stretch">
                        <div className="min-w-0">
                            <h3 className="m-0 text-[0.84rem] text-[var(--secondary-ink)]">Log out</h3>
                            <p className="mt-1 mb-0 text-[0.7rem] text-[var(--muted)]">Sign out on this device.</p>
                        </div>
                        <button type="button" className="inline-flex min-h-11 cursor-pointer items-center justify-center gap-[9px] rounded-xl border border-white/55 bg-white/30 px-[18px] font-extrabold text-[var(--control-ink)] shadow-[inset_0_1px_0_rgba(255,255,255,0.68)] transition-[border-color,color,transform] duration-150 hover:border-[#f0a2aa] hover:text-[#b33a47] active:scale-[0.95] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559] motion-reduce:transition-none max-[430px]:w-full [&_svg]:size-5 [&_svg]:fill-none [&_svg]:stroke-current [&_svg]:stroke-[1.8] [&_svg]:[stroke-linecap:round] [&_svg]:[stroke-linejoin:round]" onClick={handleLogout}>
                            <LogOut aria-hidden="true" />
                            <span>Logout</span>
                        </button>
                    </div>
                </section>
            </article>

            <PageFooterNote />
        </section>
    )
}
