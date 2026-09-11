import { Star } from 'lucide-react'

type StarButtonProps = {
    isStarred: boolean
    loadingAction?: 'check' | 'star' | 'unstar'
    onClick: () => void
}

export function StarButton({
    isStarred,
    loadingAction,
    onClick,
}: StarButtonProps) {
    const label = loadingAction === 'check'
        ? 'Checking…'
        : loadingAction === 'star'
            ? 'Starring…'
            : loadingAction === 'unstar'
                ? 'Unstarring…'
                : isStarred
                    ? 'Starred'
                    : 'Star'

    return (
        <button
                type="button"
                className={`glass-inset grid size-9 cursor-pointer place-items-center rounded-[11px] border p-0 transition-[transform,color,background-color] duration-150 hover:text-[var(--accent)] active:scale-[0.9] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559] disabled:cursor-wait disabled:text-[#8794a3] motion-reduce:transition-none [&_svg]:size-[18px] [&_svg]:stroke-[1.9] ${isStarred ? 'border-[#a9d5f5] text-[var(--accent)] [&_svg]:fill-current' : 'border-white/55 text-[var(--muted-strong)]'}`}
                aria-label={label}
                title={label}
                aria-pressed={isStarred}
                disabled={Boolean(loadingAction)}
                onClick={onClick}
            >
                {loadingAction
                    ? <span className="h-4 w-4 animate-spin rounded-full border-2 border-[#cadce8] border-t-current motion-reduce:animate-none" aria-hidden="true" />
                    : <Star aria-hidden="true" />}
        </button>
    )
}
