import { Cloud } from 'lucide-react'

type LoadingScreenProps = {
    title: string
    message: string
}

export function LoadingScreen({ title, message }: LoadingScreenProps) {
    return (
        <div className="relative grid min-h-svh w-full place-items-center overflow-hidden p-6 max-[760px]:px-5 max-[760px]:pb-[108px]" role="status" aria-live="polite">
            <div className="glass-surface-strong relative grid w-[min(100%,370px)] justify-items-center rounded-[26px] border border-white/80 px-[30px] pt-[38px] pb-8 text-center text-[var(--ink)] max-[760px]:rounded-[22px] max-[760px]:px-[22px] max-[760px]:pt-8 max-[760px]:pb-7">
                <div className="relative mb-[22px] h-[62px] w-[82px]" aria-hidden="true">
                    <span className="absolute top-0 left-[5px] z-[1] h-[38px] w-[38px] animate-pulse rounded-full bg-[#ffca28] shadow-[0_0_0_9px_rgba(255,202,40,0.14)] motion-reduce:animate-none" />
                    <Cloud className="absolute right-1 bottom-0 z-[2] h-[58px] w-[58px] fill-[#cfeeff] stroke-[1.7] text-[#45aef3]" />
                </div>
                <strong className="text-[1.08rem]">{title}</strong>
                <p className="mt-2 mb-0 text-[0.82rem] leading-[1.5] text-[var(--muted)]">{message}</p>
                <span className="mt-[22px] flex gap-1.5" aria-hidden="true">
                    <i className="h-[7px] w-[7px] animate-bounce rounded-full bg-[var(--accent)] motion-reduce:animate-none" />
                    <i className="h-[7px] w-[7px] animate-bounce rounded-full bg-[var(--accent)] [animation-delay:140ms] motion-reduce:animate-none" />
                    <i className="h-[7px] w-[7px] animate-bounce rounded-full bg-[var(--accent)] [animation-delay:280ms] motion-reduce:animate-none" />
                </span>
            </div>
        </div>
    )
}
