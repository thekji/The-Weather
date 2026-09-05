import { Suspense, lazy, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Sidebar, type AppPage } from '../components/Sidebar'
import { PlaceholderPage } from '../pages/PlaceholderPage'
import { StarredPage } from '../pages/StarredPage'

type PageLayoutProps = {
    page: AppPage
    children: ReactNode
    fullScreen?: boolean
}

const MapPage = lazy(() =>
    import('../pages/MapPage').then(({ MapPage }) => ({ default: MapPage })),
)

function AccountPage() {
    return (
        <PlaceholderPage
            eyebrow="Profile"
            title="Account"
            message="Your account settings will live here."
        />
    )
}

function PageLayout({ page, children, fullScreen = false }: PageLayoutProps) {
    const pageContentClassName = `page-content${fullScreen ? ' page-content--map' : ''}`

    return (
        <div className="app-shell">
            <Sidebar currentPage={page} />

            <main className={pageContentClassName}>
                {children}
            </main>
        </div>
    )
}

function StarredRoute() {
    return (
        <PageLayout page="starred">
            <StarredPage />
        </PageLayout>
    )
}

function MapRoute() {
    return (
        <PageLayout page="maps" fullScreen>
            <Suspense fallback={<div className="page-loading" role="status">Loading map</div>}>
                <MapPage />
            </Suspense>
        </PageLayout>
    )
}

function AccountRoute() {
    return (
        <PageLayout page="account">
            <AccountPage />
        </PageLayout>
    )
}

const RouteConfig = () => {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/starred" replace />} />
            <Route path="/starred" element={<StarredRoute />} />
            <Route path="/starred/*" element={<StarredRoute />} />
            <Route path="/map" element={<MapRoute />} />
            <Route path="/map/*" element={<MapRoute />} />
            <Route path="/maps" element={<MapRoute />} />
            <Route path="/maps/*" element={<MapRoute />} />
            <Route path="/account" element={<AccountRoute />} />
            <Route path="/account/*" element={<AccountRoute />} />
            <Route path="*" element={<Navigate to="/starred" replace />} />
        </Routes>
    )
}

export default RouteConfig
