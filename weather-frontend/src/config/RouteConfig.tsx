import { Suspense, lazy, useEffect, type ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { getTokenExpirationTime, hasAuthSession, isTokenActive } from '../api/auth'
import { LoadingScreen } from '../components/LoadingScreen'
import { Sidebar, type AppPage } from '../components/Sidebar'
import { AccountPage } from '../pages/AccountPage'
import { AnalyticsPage } from '../pages/AnalyticsPage'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'
import { StarredPage } from '../pages/StarredPage'
import { CommunityPage } from '../pages/CommunityPage'
import { useAuthStore } from '../stores/authStore'

type PageLayoutProps = {
    page: AppPage
    children: ReactNode
    fullScreen?: boolean
}

const MapPage = lazy(() =>
    import('../pages/MapPage').then(({ MapPage }) => ({ default: MapPage })),
)

function ProtectedRoute({ children }: { children: ReactNode }) {
    const location = useLocation()
    const token = useAuthStore((state) => state.token)
    const user = useAuthStore((state) => state.user)
    const activeSession = Boolean(token && user && isTokenActive(token))

    if (!activeSession) {
        return (
            <Navigate
                to="/login"
                replace
                state={{
                    from: `${location.pathname}${location.search}${location.hash}`,
                }}
            />
        )
    }
    return children
}

function AuthSessionMonitor() {
    const token = useAuthStore((state) => state.token)
    const clearSession = useAuthStore((state) => state.clearSession)

    useEffect(() => {
        if (!token) return

        const expirationTime = getTokenExpirationTime(token)
        if (expirationTime === null || expirationTime <= Date.now()) {
            clearSession('expired')
            return
        }

        const timeoutID = window.setTimeout(
            () => clearSession('expired'),
            expirationTime - Date.now(),
        )
        return () => window.clearTimeout(timeoutID)
    }, [clearSession, token])

    return null
}

function PublicAuthRoute({ children }: { children: ReactNode }) {
    if (hasAuthSession()) return <Navigate to="/map" replace />
    return children
}

function HomeRoute() {
    return <Navigate to={hasAuthSession() ? '/map' : '/login'} replace />
}

function PageLayout({ page, children, fullScreen = false }: PageLayoutProps) {
    const isFramedPage = page === 'account'
        || page === 'starred'
        || page === 'community'
        || page === 'analytics'
    const pageContentClassName = [
        'flex h-dvh min-h-0 min-w-0 pr-[clamp(28px,6vw,86px)] pl-28 min-[1025px]:max-[1100px]:pr-10 max-[1025px]:px-6 max-[760px]:px-5 max-[430px]:px-[15px] max-[350px]:px-2.5',
        'flex h-dvh min-h-0 min-w-0 pr-[clamp(28px,6vw,86px)] pl-32 min-[1025px]:max-[1100px]:pr-10 max-[1025px]:px-6 max-[760px]:px-5 max-[430px]:px-[15px] max-[350px]:px-2.5',
        fullScreen
            ? 'p-0! max-[760px]:p-0!'
            : isFramedPage
                ? 'framed-theme overflow-hidden py-[clamp(16px,2.5vh,24px)] max-[1025px]:pb-[var(--mobile-nav-clearance)] max-[760px]:h-auto max-[760px]:min-h-dvh max-[760px]:overflow-visible max-[760px]:pt-4'
                    : 'py-[clamp(42px,6vw,76px)] [@media(max-height:720px)_and_(min-width:761px)]:py-7 max-[760px]:pt-[42px] max-[760px]:pb-28',
        isFramedPage ? 'relative isolate bg-transparent' : '',
        'animate-fade-in',
    ].filter(Boolean).join(' ')

    return (
        <div className={`relative block h-dvh min-h-0 w-full overflow-hidden time-background text-[var(--ink)] ${isFramedPage ? 'max-[760px]:h-auto max-[760px]:min-h-dvh max-[760px]:overflow-visible' : ''}`}>
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

function CommunityRoute() {
    return <PageLayout page="community"><CommunityPage /></PageLayout>
}

function AnalyticsRoute() {
    return <PageLayout page="analytics"><AnalyticsPage /></PageLayout>
}

const RouteConfig = () => {
    return (
        <>
            <AuthSessionMonitor />
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
                <Route path="/community" element={<ProtectedRoute><CommunityRoute /></ProtectedRoute>} />
                <Route path="/analytics" element={<ProtectedRoute><AnalyticsRoute /></ProtectedRoute>} />
                <Route path="/map" element={<ProtectedRoute><MapRoute /></ProtectedRoute>} />
                <Route path="/map/*" element={<ProtectedRoute><MapRoute /></ProtectedRoute>} />
                <Route path="/maps" element={<ProtectedRoute><MapRoute /></ProtectedRoute>} />
                <Route path="/maps/*" element={<ProtectedRoute><MapRoute /></ProtectedRoute>} />
                <Route path="/account" element={<ProtectedRoute><AccountRoute /></ProtectedRoute>} />
                <Route path="/account/*" element={<ProtectedRoute><AccountRoute /></ProtectedRoute>} />
                <Route path="*" element={<HomeRoute />} />
            </Routes>
        </>
    )
}

export default RouteConfig
