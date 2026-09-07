import { AuthPage } from "./AuthPage"
import { RegisterForm } from "../components/register-form/ui/RegisterForm"
export function RegisterPage() {
    return (
        <AuthPage
            eyebrow="Create an account"
            title="Register"
            description="Save the weather for the places that matter to you."
        >
            <RegisterForm />
        </AuthPage>
    )
}
