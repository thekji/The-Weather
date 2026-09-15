import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../theme/theme'

export function PageFooterNote() {
    const { resolvedTheme } = useTheme()

    return (
        <aside
            className="day-starred-glass mt-[clamp(18px,3vh,30px)] grid shrink-0 grid-cols-[auto_minmax(0,1fr)_minmax(80px,18%)] items-center gap-[18px] rounded-3xl border border-[var(--line)] bg-[var(--soft-surface)] px-[26px] py-[clamp(14px,2vh,18px)] shadow-sm max-[1025px]:grid-cols-[auto_minmax(0,1fr)] max-[1025px]:px-5 max-[1025px]:py-[17px] max-[760px]:mt-3.5 max-[760px]:grid-cols-[28px_minmax(0,1fr)] max-[760px]:gap-2.5 max-[760px]:rounded-2xl max-[760px]:px-3.5 max-[760px]:py-3 [@media(max-height:640px)]:hidden"
            aria-label="Saved locations tip"
        >
            <span className="grid size-12 place-items-center text-[var(--footer-icon)] drop-shadow-[0_4px_10px_rgba(7,31,53,0.18)] max-[760px]:size-7 [&_svg]:size-10 [&_svg]:stroke-[1.8] max-[760px]:[&_svg]:size-7" aria-hidden="true">
                {resolvedTheme === 'night' ? <Moon /> : <Sun />}
            </span>
            <span className="grid gap-[5px] text-[0.83rem] leading-[1.4] text-[var(--footer-ink)] max-[760px]:gap-0.5 max-[760px]:text-[0.72rem] max-[760px]:leading-[1.3] [&_strong]:text-[0.96rem] [&_strong]:text-[var(--footer-strong-ink)] max-[760px]:[&_strong]:text-[0.8rem]">
                <strong className="font-extrabold">Your favourite places, <em className="italic font-bold text-[var(--accent)]">always within reach</em></strong>
                <span className="font-medium italic opacity-90">Check your saved locations and explore them live on the map.</span>
            </span>
            <span className="min-h-14 max-[1025px]:hidden" aria-hidden="true" />
        </aside>
    )
}
