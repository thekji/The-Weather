import { LogOut, Mail, MessageSquare, Star, UserRound } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'

function initials(name: string): string {
    return name
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part.charAt(0).toUpperCase())
        .join('') || 'U'
}

export function AccountPage() {
    const navigate = useNavigate()
    const user = useAuthStore((state) => state.user)
    const clearSession = useAuthStore((state) => state.clearSession)

    function handleLogout() {
        clearSession()
        navigate('/login', { replace: true })
    }

    if (!user) return null

    return (
        <section className="account-page" aria-labelledby="account-title">
            <header className="account-header">
                <p className="eyebrow">Profile</p>
                <h1 id="account-title">Account</h1>
                <p className="page-description">Your basic account information.</p>
            </header>

            <article className="account-card">
                <section className="account-profile-panel" aria-labelledby="basic-information-title">
                    <div className="account-identity">
                        <div className="account-avatar" aria-hidden="true">
                            {initials(user.name)}
                        </div>
                        <div>
                            <h2>{user.name}</h2>
                            <p>{user.email}</p>
                        </div>
                    </div>

                    <div className="account-section-heading">
                        <h2 id="basic-information-title">Basic information</h2>
                        <p>Your account details.</p>
                    </div>

                    <dl className="account-details">
                        <div>
                            <dt><UserRound aria-hidden="true" />Name</dt>
                            <dd>{user.name}</dd>
                        </div>
                        <div>
                            <dt><Mail aria-hidden="true" />Email address</dt>
                            <dd>{user.email}</dd>
                        </div>
                    </dl>
                </section>

                <section className="account-overview" aria-labelledby="account-overview-title">
                    <div className="account-section-heading account-overview-heading">
                        <h2 id="account-overview-title">Account overview</h2>
                        <p>Your activity on the weather app.</p>
                    </div>

                    <div className="account-stat-list">
                        <article className="account-stat">
                            <span className="account-stat-icon account-stat-icon-star">
                                <Star aria-hidden="true" />
                            </span>
                            <div>
                                <h3>Starred locations</h3>
                                <p><strong>0</strong> Locations saved</p>
                            </div>
                        </article>

                        <article className="account-stat">
                            <span className="account-stat-icon account-stat-icon-post">
                                <MessageSquare aria-hidden="true" />
                            </span>
                            <div>
                                <h3>Posts</h3>
                                <p><strong>0</strong> Posts shared</p>
                            </div>
                        </article>
                    </div>

                    <div className="account-actions">
                        <div>
                            <h3>Log out</h3>
                            <p>Sign out on this device.</p>
                        </div>
                        <button type="button" className="account-logout" onClick={handleLogout}>
                            <LogOut aria-hidden="true" />
                            <span>Logout</span>
                        </button>
                    </div>
                </section>
            </article>
        </section>
    )
}
