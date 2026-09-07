import { Cloud } from 'lucide-react'

type LoadingScreenProps = {
    title: string
    message: string
}

export function LoadingScreen({ title, message }: LoadingScreenProps) {
    return (
        <div className="page-loading" role="status" aria-live="polite">
            <div className="page-loading-card">
                <div className="page-loading-weather" aria-hidden="true">
                    <span className="page-loading-sun" />
                    <Cloud />
                </div>
                <strong>{title}</strong>
                <p>{message}</p>
                <span className="page-loading-dots" aria-hidden="true">
                    <i />
                    <i />
                    <i />
                </span>
            </div>
        </div>
    )
}
