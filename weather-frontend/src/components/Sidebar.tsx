import { LogOut } from 'lucide-react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { WeatherBrandIcon } from './WeatherBrandIcon'

export type AppPage = 'starred' | 'maps' | 'community' | 'account'

type SidebarProps = {
    currentPage: AppPage
}

type IconName = AppPage

const navigation: ReadonlyArray<{
    label: string
    href: string
    icon: IconName
    page: AppPage
}> = [
    { label: 'Map', href: '/map', icon: 'maps', page: 'maps' },
    { label: 'Starred', href: '/starred', icon: 'starred', page: 'starred' },
    { label: 'Community', href: '/community', icon: 'community', page: 'community' },
    { label: 'Account', href: '/account', icon: 'account', page: 'account' },
]

function NavigationIcon({ name }: { name: IconName }) {
    const svgClassName = 'size-5 overflow-visible fill-none stroke-current stroke-[2] [stroke-linecap:round] [stroke-linejoin:round] max-[1025px]:size-[20px]'

    if (name === 'starred') {
        return (
            <svg className={svgClassName} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path d="m12 3 2.7 5.47 6.03.88-4.36 4.25 1.03 6-5.4-2.84-5.4 2.84 1.03-6-4.36-4.25 6.03-.88L12 3Z" />
            </svg>
        )
    }

    if (name === 'maps') {
        return (
            <svg className={svgClassName} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path d="m9 18-6 3V6l6-3 6 3 6-3v15l-6 3-6-3Z" />
                <path d="M9 3v15M15 6v15" />
            </svg>
        )
    }

    if (name === 'community') {
        return <svg className={svgClassName} viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h16v12H8l-4 3V5Z" /><path d="M8 9h8M8 13h5" /></svg>
    }

    return (
        <svg className={svgClassName} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <circle cx="12" cy="8" r="4" />
            <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
        </svg>
    )
}

export function Sidebar({ currentPage }: SidebarProps) {
    const navigate = useNavigate()
    const clearSession = useAuthStore((state) => state.clearSession)

    function handleLogout() {
        clearSession()
        navigate('/login', { replace: true })
    }

    return (
        <aside className="app-navigation liquid-glass fixed top-4 bottom-4 left-4 z-[800] isolate flex w-24 min-w-0 flex-col items-center overflow-hidden rounded-3xl px-2.5 pt-4 pb-3 max-[1025px]:top-auto max-[1025px]:right-3 max-[1025px]:left-3 max-[1025px]:w-auto max-[1025px]:flex-row max-[1025px]:items-center max-[1025px]:gap-1.5 max-[1025px]:rounded-[23px] max-[1025px]:p-2">
            <Link
                className="relative z-[1] flex size-11 items-center justify-center text-[var(--ink)] no-underline focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#1596f566] max-[1025px]:hidden"
                to="/map"
                aria-label="Weather home"
            >
                <span className="size-10 shrink-0"><WeatherBrandIcon /></span>
            </Link>

            <nav className="relative z-[1] mt-6 min-h-0 flex-1 w-full overflow-x-hidden overflow-y-auto overscroll-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden max-[1025px]:m-0 max-[1025px]:overflow-visible" aria-label="Primary navigation">
                <ul className="m-0 grid w-full min-w-0 list-none gap-2.5 p-0 max-[1025px]:grid-cols-4 max-[1025px]:gap-1">
                    {navigation.map((item) => {
                        const isCurrent = item.page === currentPage

                        return (
                            <li className="min-w-0" key={item.page}>
                                <NavLink
                                    className={`flex min-h-[64px] w-full min-w-0 flex-col items-center justify-center gap-1 rounded-2xl border px-1.5 py-2 text-[0.68rem] font-extrabold no-underline transition-all duration-200 active:scale-95 focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#1596f566] motion-reduce:transition-none max-[1025px]:min-h-14 max-[1025px]:rounded-[17px] max-[1025px]:p-1 max-[1025px]:text-[0.64rem] ${isCurrent ? 'border-[var(--active-border)] bg-[var(--active-surface)] text-[var(--active-ink)] shadow-sm' : 'border-transparent text-[var(--muted)] hover:bg-[var(--surface-hover)] hover:text-[var(--ink)]'}`}
                                    to={item.href}
                                    aria-current={isCurrent ? 'page' : undefined}
                                >
                                    <span className={`grid size-6 place-items-center transition-transform duration-200 ${isCurrent ? 'scale-105 text-[var(--active-icon)]' : ''}`}>
                                        <NavigationIcon name={item.icon} />
                                    </span>
                                    <span>{item.label}</span>
                                </NavLink>
                            </li>
                        )
                    })}
                </ul>
            </nav>

            <button
                type="button"
                className="relative z-[1] mt-auto grid size-11 shrink-0 cursor-pointer place-items-center rounded-xl border border-[var(--line)] bg-[var(--soft-surface)] text-[var(--muted-strong)] shadow-sm transition-all hover:bg-red-500/15 hover:text-red-600 active:scale-90 focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#1596f566] max-[1025px]:m-0 max-[1025px]:size-11 [&_svg]:size-5 [&_svg]:fill-none [&_svg]:stroke-[2]"
                aria-label="Log out"
                title="Log out"
                onClick={handleLogout}
            >
                <LogOut aria-hidden="true" />
            </button>
        </aside>
    )
}
