import { Suspense, lazy, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { hasAuthSession } from '../api/auth'
import { LoadingScreen } from '../components/LoadingScreen'
import { Sidebar, type AppPage } from '../components/Sidebar'
import { AccountPage } from '../pages/AccountPage'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'
import { StarredPage } from '../pages/StarredPage'

type PageLayoutProps = {
    page: AppPage
    children: ReactNode
    fullScreen?: boolean
}

const MapPage = lazy(() =>
    import('../pages/MapPage').then(({ MapPage }) => ({ default: MapPage })),
)

function ProtectedRoute({ children }: { children: ReactNode }) {
    if (!hasAuthSession()) return <Navigate to="/login" replace />
    return children
}

function PublicAuthRoute({ children }: { children: ReactNode }) {
    if (hasAuthSession()) return <Navigate to="/map" replace />
    return children
}

function HomeRoute() {
    return <Navigate to={hasAuthSession() ? '/map' : '/login'} replace />
}

function PageLayout({ page, children, fullScreen = false }: PageLayoutProps) {
    const isFramedPage = page === 'account' || page === 'starred'
    const pageContentClassName = [
        'flex h-dvh min-h-0 min-w-0 pr-[clamp(28px,6vw,86px)] pl-36 min-[1025px]:max-[1100px]:pr-10 min-[1025px]:max-[1100px]:pl-[132px] max-[1025px]:px-6 max-[760px]:px-5 max-[430px]:px-[15px] max-[350px]:px-2.5',
        fullScreen
            ? 'p-0! max-[760px]:p-0!'
            : isFramedPage
                ? 'framed-theme overflow-hidden py-[clamp(16px,2.5vh,24px)] max-[1025px]:pb-[var(--mobile-nav-clearance)] max-[760px]:pt-4'
                    : 'py-[clamp(42px,6vw,76px)] [@media(max-height:720px)_and_(min-width:761px)]:py-7 max-[760px]:pt-[42px] max-[760px]:pb-28',
        isFramedPage ? 'relative isolate bg-transparent' : '',
    ].filter(Boolean).join(' ')

    return (
        <div className="relative block h-dvh min-h-0 w-full overflow-hidden bg-[var(--app-background)] text-[var(--ink)]">
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
            <Suspense fallback={(
                <LoadingScreen
                    title="Preparing your map"
                    message="Loading the map and location tools…"
                />
            )}>
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
            <Route path="/" element={<HomeRoute />} />
            <Route path="/login" element={(
                <PublicAuthRoute>
                    <LoginPage />
                </PublicAuthRoute>
            )} />
            <Route path="/register" element={(
                <PublicAuthRoute>
                    <RegisterPage />
                </PublicAuthRoute>
            )} />
            <Route path="/starred" element={<ProtectedRoute><StarredRoute /></ProtectedRoute>} />
            <Route path="/starred/*" element={<ProtectedRoute><StarredRoute /></ProtectedRoute>} />
            <Route path="/map" element={<ProtectedRoute><MapRoute /></ProtectedRoute>} />
            <Route path="/map/*" element={<ProtectedRoute><MapRoute /></ProtectedRoute>} />
            <Route path="/maps" element={<ProtectedRoute><MapRoute /></ProtectedRoute>} />
            <Route path="/maps/*" element={<ProtectedRoute><MapRoute /></ProtectedRoute>} />
            <Route path="/account" element={<ProtectedRoute><AccountRoute /></ProtectedRoute>} />
            <Route path="/account/*" element={<ProtectedRoute><AccountRoute /></ProtectedRoute>} />
            <Route path="*" element={<HomeRoute />} />
        </Routes>
    )
}

export default RouteConfig
