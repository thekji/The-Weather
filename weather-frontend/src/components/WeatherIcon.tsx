export type WeatherKind = 'sunny' | 'partly-cloudy' | 'rain' | 'clear-night'

type WeatherIconProps = {
    kind: WeatherKind
}

export function WeatherIcon({ kind }: WeatherIconProps) {
    const iconClassName = 'block size-[72px] overflow-visible drop-shadow-[0_9px_10px_rgba(32,43,60,0.08)] max-[760px]:size-[60px] max-[430px]:size-[52px]'

    if (kind === 'sunny') {
        return (
            <svg className={iconClassName} viewBox="0 0 72 72" aria-hidden="true" focusable="false">
                <circle className="fill-[#ffe999] opacity-70" cx="36" cy="36" r="25" />
                <circle className="fill-[#ffce32]" cx="36" cy="36" r="18" />
            </svg>
        )
    }

    if (kind === 'clear-night') {
        return (
            <svg className={iconClassName} viewBox="0 0 72 72" aria-hidden="true" focusable="false">
                <path className="fill-[#7c8bac]" d="M51 48.5A24 24 0 0 1 27.3 13 25 25 0 1 0 51 48.5Z" />
                <circle className="fill-[#ffcf3d]" cx="51" cy="20" r="3" />
                <circle className="fill-[#adc8e8]" cx="58" cy="31" r="2" />
            </svg>
        )
    }

    if (kind === 'rain') {
        return (
            <svg className={iconClassName} viewBox="0 0 72 72" aria-hidden="true" focusable="false">
                <circle className="fill-[#d9e6ee]" cx="31" cy="32" r="15" />
                <circle className="fill-[#f7fbfd]" cx="44" cy="35" r="14" />
                <rect className="fill-[#f7fbfd]" x="18" y="34" width="39" height="14" rx="7" />
                <path className="fill-none stroke-[#35aaf7] stroke-[3.2] [stroke-linecap:round]" d="m26 54-3 7M38 54l-3 7M50 54l-3 7" />
            </svg>
        )
    }

    return (
        <svg className={iconClassName} viewBox="0 0 72 72" aria-hidden="true" focusable="false">
            <circle className="fill-[#ffe999] opacity-70" cx="46" cy="26" r="17" />
            <circle className="fill-[#ffce32]" cx="46" cy="26" r="12" />
            <circle className="fill-[#d9e6ee]" cx="29" cy="39" r="14" />
            <circle className="fill-[#f7fbfd]" cx="42" cy="41" r="12" />
            <rect className="fill-[#f7fbfd]" x="17" y="40" width="37" height="13" rx="6.5" />
        </svg>
    )
}
