type PlaceholderPageProps = {
    eyebrow: string
    title: string
    message: string
}

export function PlaceholderPage({ eyebrow, title, message }: PlaceholderPageProps) {
    return (
        <section className="flex w-[min(100%,1040px)] flex-col items-start justify-center self-stretch max-[760px]:min-h-[calc(100svh-154px)]" aria-labelledby="placeholder-title">
            <div className="mb-7 flex size-[76px] items-end gap-[7px] rounded-3xl bg-[var(--soft-surface)] p-[19px]" aria-hidden="true">
                <span className="h-[17px] w-2 rounded-lg bg-[var(--accent)] opacity-40" />
                <span className="h-[30px] w-2 rounded-lg bg-[var(--accent)] opacity-70" />
                <span className="h-[23px] w-2 rounded-lg bg-[var(--accent)]" />
            </div>
            <p className="mt-0 mb-[9px] text-xs font-extrabold tracking-[0.14em] text-[var(--eyebrow)] uppercase">{eyebrow}</p>
            <h1 className="m-0 text-[clamp(2.25rem,5vw,4.25rem)] leading-[0.98] font-extrabold tracking-[0.01em] text-[var(--ink)] max-[760px]:text-[clamp(2.35rem,12vw,3.6rem)]" id="placeholder-title">{title}</h1>
            <p className="mt-[18px] mb-0 text-base text-[var(--muted)]">{message}</p>
        </section>
    )
}
