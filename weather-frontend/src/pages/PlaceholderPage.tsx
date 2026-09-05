type PlaceholderPageProps = {
    eyebrow: string
    title: string
    message: string
}

export function PlaceholderPage({ eyebrow, title, message }: PlaceholderPageProps) {
    return (
        <section className="placeholder-page" aria-labelledby="placeholder-title">
            <div className="placeholder-mark" aria-hidden="true">
                <span />
                <span />
                <span />
            </div>
            <p className="eyebrow">{eyebrow}</p>
            <h1 id="placeholder-title">{title}</h1>
            <p>{message}</p>
        </section>
    )
}
