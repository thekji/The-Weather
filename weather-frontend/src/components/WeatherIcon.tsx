export type WeatherKind = 'sunny' | 'partly-cloudy' | 'rain' | 'clear-night'

type WeatherIconProps = {
    kind: WeatherKind
}

export function WeatherIcon({ kind }: WeatherIconProps) {
    if (kind === 'sunny') {
        return (
            <svg className="weather-glyph" viewBox="0 0 72 72" aria-hidden="true" focusable="false">
                <circle className="sun-halo" cx="36" cy="36" r="25" />
                <circle className="sun-core" cx="36" cy="36" r="18" />
            </svg>
        )
    }

    if (kind === 'clear-night') {
        return (
            <svg className="weather-glyph" viewBox="0 0 72 72" aria-hidden="true" focusable="false">
                <path className="moon" d="M51 48.5A24 24 0 0 1 27.3 13 25 25 0 1 0 51 48.5Z" />
                <circle className="night-star" cx="51" cy="20" r="3" />
                <circle className="night-star-small" cx="58" cy="31" r="2" />
            </svg>
        )
    }

    if (kind === 'rain') {
        return (
            <svg className="weather-glyph" viewBox="0 0 72 72" aria-hidden="true" focusable="false">
                <circle className="cloud-back" cx="31" cy="32" r="15" />
                <circle className="cloud-front" cx="44" cy="35" r="14" />
                <rect className="cloud-front" x="18" y="34" width="39" height="14" rx="7" />
                <path className="rain-drop" d="m26 54-3 7M38 54l-3 7M50 54l-3 7" />
            </svg>
        )
    }

    return (
        <svg className="weather-glyph" viewBox="0 0 72 72" aria-hidden="true" focusable="false">
            <circle className="small-sun-halo" cx="46" cy="26" r="17" />
            <circle className="small-sun-core" cx="46" cy="26" r="12" />
            <circle className="cloud-back" cx="29" cy="39" r="14" />
            <circle className="cloud-front" cx="42" cy="41" r="12" />
            <rect className="cloud-front" x="17" y="40" width="37" height="13" rx="6.5" />
        </svg>
    )
}
