import { Laptop, LogOut, Mail, MessageSquare, Moon, Star, Sun, UserRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getStarredLocations } from '../api/stars'
import { getCommunityPostCount } from '../components/community-post/service/postService'
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
    const [postCount, setPostCount] = useState<number | null>(null)
    const [postCountError, setPostCountError] = useState(false)

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

    useEffect(() => {
        if (!user) return
        const controller = new AbortController()
        void getCommunityPostCount(user.userID, controller.signal)
            .then((count) => {
                if (!controller.signal.aborted) setPostCount(count)
            })
            .catch(() => {
                if (!controller.signal.aborted) setPostCountError(true)
            })
        return () => controller.abort()
    }, [user])

    function handleLogout() {
        clearSession()
        navigate('/login', { replace: true })
    }

    if (!user) return null

    const panelClassName = 'day-starred-glass bg-[var(--surface)] rounded-3xl border border-[var(--line)] p-6 shadow-sm max-[430px]:p-4'
    const statClassName = 'bg-[var(--soft-surface)] flex min-h-[90px] items-center gap-4 rounded-2xl border border-[var(--line)] p-4'
    const detailTermClassName = 'flex items-center gap-2.5 text-xs font-extrabold text-[var(--secondary-ink)] [&_svg]:size-4 [&_svg]:text-[var(--accent)]'

    return (
        <section className="relative z-[1] mx-auto flex h-full min-h-0 w-full max-w-[1600px] flex-col animate-fade-in max-[760px]:h-auto" aria-labelledby="account-title">
            <header className="mb-[clamp(20px,3.5vh,34px)] flex shrink-0 items-start justify-between gap-7 max-[760px]:mb-4 max-[430px]:block">
                <div className="min-w-0 flex-1">
                    <p className="mt-0 mb-[9px] w-fit text-xs font-extrabold tracking-[0.14em] text-[var(--eyebrow)] uppercase max-[760px]:mb-1.5 max-[760px]:text-[0.68rem]">Profile</p>
                    <div className="flex items-center gap-[18px] max-[760px]:flex-wrap max-[760px]:gap-3">
                        <h1 className="m-0 w-fit text-[clamp(2.25rem,4vw,3.75rem)] leading-[0.98] font-extrabold tracking-[0.01em] text-[var(--ink)] [@media(max-height:720px)_and_(min-width:761px)]:text-[clamp(2.25rem,6vh,3.35rem)] max-[760px]:text-[clamp(2rem,9vw,2.65rem)]" id="account-title">Account</h1>
                    </div>
                    <p className="mt-[15px] mb-0 text-base leading-[1.6] italic font-medium text-[var(--muted)] max-[760px]:mt-2.5 max-[760px]:text-[0.88rem] max-[760px]:leading-[1.45]">Manage your profile details and app settings.</p>
                </div>
            </header>

            <div className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto overscroll-contain pr-1 max-[760px]:flex-none max-[760px]:overflow-visible max-[760px]:pr-0">
                <article className="grid grid-cols-[minmax(0,1.1fr)_minmax(320px,0.9fr)] gap-5 pt-1 max-[1025px]:grid-cols-1">
                    <section className={panelClassName} aria-labelledby="basic-information-title">
                        <div className="flex items-center gap-4 border-b border-[var(--line)] pb-5">
                            <div className="grid size-16 shrink-0 place-items-center rounded-full border border-[var(--line)] bg-[var(--active-surface)] text-xl font-extrabold text-[var(--active-icon)]" aria-hidden="true">{initials(user.name)}</div>
                            <div className="min-w-0">
                                <h2 className="m-0 text-lg font-bold text-[var(--ink)]">{user.name}</h2>
                                <p className="mt-1 mb-0 truncate text-xs font-medium text-[var(--muted)]">{user.email}</p>
                            </div>
                        </div>

                        <div className="mt-5">
                            <h2 className="m-0 text-base font-bold text-[var(--ink)]" id="basic-information-title">Basic Information</h2>
                            <p className="mt-1 mb-0 text-xs text-[var(--muted)]">Your account details.</p>
                        </div>

                        <dl className="mt-4 mb-0 grid gap-2 rounded-2xl border border-[var(--line)] bg-[var(--soft-surface)] p-4">
                            <div className="flex items-center justify-between gap-4 py-1.5">
                                <dt className={detailTermClassName}><UserRound aria-hidden="true" />Name</dt>
                                <dd className="m-0 text-xs font-extrabold text-[var(--ink)]">{user.name}</dd>
                            </div>
                            <div className="flex items-center justify-between gap-4 border-t border-[var(--line)] pt-3 py-1.5">
                                <dt className={detailTermClassName}><Mail aria-hidden="true" />Email</dt>
                                <dd className="m-0 truncate text-xs font-extrabold text-[var(--ink)]">{user.email}</dd>
                            </div>
                        </dl>
                    </section>

                    <section className={`${panelClassName} flex flex-col gap-5`} aria-labelledby="account-overview-title">
                        <div>
                            <h2 className="m-0 text-base font-bold text-[var(--ink)]" id="account-overview-title">Account Overview</h2>
                            <p className="mt-1 mb-0 text-xs text-[var(--muted)]">Your activity on the weather app.</p>
                        </div>

                        <div className="grid grid-cols-2 gap-3 max-[520px]:grid-cols-1">
                            <article className={statClassName}>
                                <span className="grid size-9 shrink-0 place-items-center text-[#f59e0b]"><Star className="size-6 fill-current" aria-hidden="true" /></span>
                                <div className="min-w-0">
                                    <h3 className="m-0 text-xs font-bold text-[var(--secondary-ink)]">Starred places</h3>
                                    <p className="mt-1 mb-0 text-xs font-medium text-[var(--muted)]">
                                        <strong className="mr-1 text-lg font-extrabold text-[var(--ink)]">{starredLocationCountError ? '—' : starredLocationCount ?? '…'}</strong>
                                        {starredLocationCountError ? 'Saved' : starredLocationCount === 1 ? 'saved' : 'saved'}
                                    </p>
                                </div>
                            </article>

                            <article className={statClassName}>
                                <span className="grid size-9 shrink-0 place-items-center text-[#6366f1]"><MessageSquare className="size-6" aria-hidden="true" /></span>
                                <div className="min-w-0">
                                    <h3 className="m-0 text-xs font-bold text-[var(--secondary-ink)]">Posts</h3>
                                    <p className="mt-1 mb-0 text-xs font-medium text-[var(--muted)]">
                                        <strong className="mr-1 text-lg font-extrabold text-[var(--ink)]">{postCountError ? '—' : postCount ?? '…'}</strong>
                                        {postCountError ? 'Posts' : postCount === 1 ? 'shared' : 'shared'}
                                    </p>
                                </div>
                            </article>
                        </div>

                        <section className="rounded-2xl border border-[var(--line)] bg-[var(--soft-surface)] p-3.5" aria-labelledby="appearance-title">
                            <div className="mb-3">
                                <h3 className="m-0 text-xs font-bold text-[var(--secondary-ink)]" id="appearance-title">Appearance</h3>
                                <p className="mt-0.5 mb-0 text-[0.68rem] italic text-[var(--muted)]">Currently using {resolvedTheme} theme.</p>
                            </div>
                            <div className="grid grid-cols-3 gap-2" role="group" aria-label="Color theme">
                                {themeOptions.map((option) => {
                                    const Icon = option.icon
                                    const selected = preference === option.value
                                    return (
                                        <button
                                            type="button"
                                            className={`inline-flex min-h-9 cursor-pointer items-center justify-center gap-1.5 rounded-xl border px-2 text-xs font-bold transition-all ${selected ? 'border-[var(--active-border)] bg-[var(--active-surface)] text-[var(--active-ink)] shadow-sm' : 'border-transparent bg-transparent text-[var(--muted)] hover:bg-[var(--surface-hover)] hover:text-[var(--ink)]'}`}
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

                        <div className="flex items-center justify-between gap-4 rounded-2xl border border-[var(--line)] bg-[var(--soft-surface)] p-4 max-[430px]:flex-col max-[430px]:items-stretch">
                            <div>
                                <h3 className="m-0 text-xs font-bold text-[var(--secondary-ink)]">Log out</h3>
                                <p className="mt-0.5 mb-0 text-[0.68rem] text-[var(--muted)]">Sign out on this device.</p>
                            </div>
                            <button type="button" className="inline-flex min-h-10 cursor-pointer items-center justify-center gap-2 rounded-xl border border-red-500/30 bg-red-500/10 px-4 text-xs font-bold text-red-500 hover:bg-red-500/20 transition-all max-[430px]:w-full" onClick={handleLogout}>
                                <LogOut className="size-4" aria-hidden="true" />
                                <span>Logout</span>
                            </button>
                        </div>
                    </section>
                </article>
            </div>

            <PageFooterNote />
        </section>
    )
}
