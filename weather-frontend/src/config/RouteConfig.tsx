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
    const pageContentClassName = [
        'page-content',
        fullScreen ? 'page-content--map' : '',
        page === 'account' || page === 'starred' ? 'page-content--gradient' : '',
    ].filter(Boolean).join(' ')

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
