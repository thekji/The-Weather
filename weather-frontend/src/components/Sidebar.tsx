import { LogOut } from 'lucide-react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { WeatherBrandIcon } from './WeatherBrandIcon'

export type AppPage = 'starred' | 'maps' | 'account'

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
    { label: 'Account', href: '/account', icon: 'account', page: 'account' },
]

function NavigationIcon({ name, active }: { name: IconName; active: boolean }) {
    const svgClassName = 'size-6 overflow-visible fill-none stroke-current stroke-[1.8] [stroke-linecap:round] [stroke-linejoin:round] max-[1025px]:size-[21px]'

    if (name === 'starred') {
        return (
            <svg className={svgClassName} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                    className={active ? 'fill-current' : undefined}
                    d="m12 3 2.7 5.47 6.03.88-4.36 4.25 1.03 6-5.4-2.84-5.4 2.84 1.03-6-4.36-4.25 6.03-.88L12 3Z"
                />
            </svg>
        )
    }

    if (name === 'maps') {
        return (
            <svg className={svgClassName} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path
                    className={active ? 'fill-current' : undefined}
                    d="m9 18-6 3V6l6-3 6 3 6-3v15l-6 3-6-3Z"
                />
                <path className={active ? 'stroke-[var(--surface)]' : undefined} d="M9 3v15M15 6v15" />
            </svg>
        )
    }

    return (
        <svg className={svgClassName} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <circle className={active ? 'fill-current' : undefined} cx="12" cy="8" r="4" />
            <path className={active ? 'fill-current' : undefined} d="M4.5 21a7.5 7.5 0 0 1 15 0" />
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
        <aside className="glass-surface fixed top-4 bottom-4 left-4 z-[800] isolate flex w-24 min-w-0 flex-col overflow-hidden rounded-3xl border border-white/60 px-2.5 pt-5 pb-4 before:pointer-events-none before:absolute before:inset-0 before:z-0 before:rounded-[inherit] before:bg-[linear-gradient(180deg,rgba(255,255,255,0.16),rgba(85,145,192,0.07)),radial-gradient(circle_at_92%_92%,rgba(41,111,172,0.14),transparent_42%)] after:pointer-events-none after:absolute after:inset-px after:z-0 after:rounded-[22px] after:bg-[linear-gradient(115deg,rgba(255,255,255,0.24),transparent_32%),radial-gradient(circle_at_18%_3%,rgba(255,255,255,0.34),transparent_24%)] max-[1025px]:top-auto max-[1025px]:right-3 max-[1025px]:bottom-3 max-[1025px]:left-3 max-[1025px]:w-auto max-[1025px]:flex-row max-[1025px]:items-center max-[1025px]:gap-1.5 max-[1025px]:rounded-[23px] max-[1025px]:p-2">
            <Link
                className="relative z-[1] flex min-h-12 items-center justify-center text-[var(--ink)] no-underline focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#1596f566] max-[1025px]:hidden"
                to="/map"
                aria-label="Weather home"
            >
                <span className="size-[42px] shrink-0"><WeatherBrandIcon /></span>
            </Link>

            <nav className="relative z-[1] mt-[52px] max-[1025px]:m-0 max-[1025px]:flex-1" aria-label="Primary navigation">
                <ul className="m-0 grid list-none gap-3 p-0 max-[1025px]:grid-cols-3 max-[1025px]:gap-[5px]">
                    {navigation.map((item) => {
                        const isCurrent = item.page === currentPage

                        return (
                            <li key={item.page}>
                                <NavLink
                                    className={`flex min-h-[76px] flex-col items-center justify-center gap-2 rounded-[18px] border px-1.5 py-2 text-[0.76rem] font-extrabold no-underline transition-[transform,color,background-color,border-color] duration-150 active:scale-[0.94] focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#1596f566] motion-reduce:transition-none max-[1025px]:min-h-16 max-[1025px]:gap-1 max-[1025px]:rounded-[17px] max-[1025px]:p-1.5 max-[1025px]:text-[0.68rem] ${isCurrent ? 'border-[var(--active-border)] bg-[var(--active-surface)] text-[var(--ink)] shadow-[0_10px_24px_rgba(25,73,115,0.11),inset_0_1px_0_rgba(255,255,255,0.38)]' : 'border-transparent text-[var(--muted)] hover:border-[var(--active-border)] hover:bg-[var(--active-surface)] hover:text-[var(--ink)]'}`}
                                    to={item.href}
                                    aria-current={isCurrent ? 'page' : undefined}
                                >
                                    <span className={`grid size-[30px] place-items-center max-[1025px]:size-[21px] ${isCurrent ? 'text-[var(--active-icon)]' : ''}`}>
                                        <NavigationIcon name={item.icon} active={isCurrent} />
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
                className="relative z-[1] m-auto mb-0 grid size-[42px] shrink-0 cursor-pointer place-items-center rounded-[13px] border border-white/50 bg-white/20 p-0 text-[var(--muted-strong)] shadow-[0_8px_20px_rgba(21,50,84,0.1),inset_0_1px_0_rgba(255,255,255,0.56)] transition-[transform,color,background-color] duration-150 hover:bg-white/32 hover:text-[#ff9aa8] active:scale-[0.9] focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#1596f566] motion-reduce:transition-none max-[1025px]:m-0 max-[1025px]:size-[46px] max-[1025px]:rounded-[15px] [&_svg]:size-[19px] [&_svg]:fill-none [&_svg]:stroke-[1.9]"
                aria-label="Log out"
                title="Log out"
                onClick={handleLogout}
            >
                <LogOut aria-hidden="true" />
            </button>
        </aside>
    )
}
