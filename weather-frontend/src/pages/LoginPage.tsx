import { AuthPage } from "./AuthPage"
import { LoginForm } from "../components/login-form/ui/LoginForm"

export function LoginPage() {
    return (
        <AuthPage
            eyebrow="Welcome back"
            title="Login"
            description="Sign in to see the places you have saved."
        >
            <LoginForm />
        </AuthPage>
    )
}
