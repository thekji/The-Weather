import { Link, NavLink } from 'react-router-dom'

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
    { label: 'Starred', href: '/starred', icon: 'starred', page: 'starred' },
    { label: 'Map', href: '/map', icon: 'maps', page: 'maps' },
    { label: 'Account', href: '/account', icon: 'account', page: 'account' },
]

function NavigationIcon({ name }: { name: IconName }) {
    if (name === 'starred') {
        return (
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path d="m12 3 2.7 5.47 6.03.88-4.36 4.25 1.03 6-5.4-2.84-5.4 2.84 1.03-6-4.36-4.25 6.03-.88L12 3Z" />
            </svg>
        )
    }

    if (name === 'maps') {
        return (
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path d="m9 18-6 3V6l6-3 6 3 6-3v15l-6 3-6-3Z" />
                <path d="M9 3v15M15 6v15" />
            </svg>
        )
    }

    return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <circle cx="12" cy="8" r="4" />
            <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
        </svg>
    )
}

function BrandMark() {
    return (
        <svg viewBox="0 0 44 44" aria-hidden="true" focusable="false">
            <circle cx="26" cy="17" r="8" className="brand-sun" />
            <path
                className="brand-cloud"
                d="M31.7 32H12.4a7.4 7.4 0 0 1-.55-14.78A10.5 10.5 0 0 1 31.5 19a6.52 6.52 0 0 1 .2 13Z"
            />
        </svg>
    )
}

export function Sidebar({ currentPage }: SidebarProps) {
    return (
        <aside className="sidebar">
            <Link className="brand" to="/starred" aria-label="Weather home">
                <span className="brand-mark"><BrandMark /></span>
            </Link>

            <nav className="primary-nav" aria-label="Primary navigation">
                <ul>
                    {navigation.map((item) => {
                        const isCurrent = item.page === currentPage

                        return (
                            <li key={item.page}>
                                <NavLink
                                    className={`nav-link${isCurrent ? ' nav-link-active' : ''}`}
                                    to={item.href}
                                    aria-current={isCurrent ? 'page' : undefined}
                                >
                                    <span className={`nav-icon nav-icon-${item.icon}`}>
                                        <NavigationIcon name={item.icon} />
                                    </span>
                                    <span>{item.label}</span>
                                </NavLink>
                            </li>
                        )
                    })}
                </ul>
            </nav>

        </aside>
    )
}
