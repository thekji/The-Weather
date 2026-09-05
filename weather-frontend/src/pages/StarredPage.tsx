import { WeatherIcon, type WeatherKind } from '../components/WeatherIcon'

type StarredLocation = {
    city: string
    country: string
    localTime: string
    condition: string
    temperature: number
    weather: WeatherKind
}

const starredLocations: ReadonlyArray<StarredLocation> = [
    {
        city: 'Ho Chi Minh City',
        country: 'Vietnam',
        localTime: '10:23',
        condition: 'Sunny',
        temperature: 31,
        weather: 'sunny',
    },
    {
        city: 'Melbourne',
        country: 'Australia',
        localTime: '13:23',
        condition: 'Partly cloudy',
        temperature: 22,
        weather: 'partly-cloudy',
    },
    {
        city: 'London',
        country: 'United Kingdom',
        localTime: '04:23',
        condition: 'Light rain',
        temperature: 16,
        weather: 'rain',
    },
    {
        city: 'Tokyo',
        country: 'Japan',
        localTime: '12:23',
        condition: 'Clear night',
        temperature: 25,
        weather: 'clear-night',
    },
]

function SavedStar() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path d="m12 3 2.7 5.47 6.03.88-4.36 4.25 1.03 6-5.4-2.84-5.4 2.84 1.03-6-4.36-4.25 6.03-.88L12 3Z" />
        </svg>
    )
}

export function StarredPage() {
    return (
        <section className="starred-page" aria-labelledby="starred-title">
            <header className="page-header">
                <div>
                    <p className="eyebrow">Saved locations</p>
                    <h1 id="starred-title">Starred places</h1>
                    <p className="page-description">A quick look at the places you care about.</p>
                </div>
                <p className="location-count" aria-label="4 saved places">
                    <span>4</span> places
                </p>
            </header>

            <ul className="weather-list" aria-label="Starred locations">
                {starredLocations.map((location) => (
                    <li key={location.city}>
                        <article className="weather-card">
                            <div className="weather-art">
                                <WeatherIcon kind={location.weather} />
                            </div>

                            <div className="place-copy">
                                <div className="place-title-row">
                                    <h2>{location.city}</h2>
                                    <span className="saved-indicator" title="Starred">
                                        <SavedStar />
                                        <span className="sr-only">Starred location</span>
                                    </span>
                                </div>
                                <p>{location.country} <span aria-hidden="true">·</span> {location.localTime}</p>
                                <span className="condition">{location.condition}</span>
                            </div>

                            <p className="temperature" aria-label={`${location.temperature} degrees Celsius`}>
                                {location.temperature}<span>°</span>
                            </p>
                        </article>
                    </li>
                ))}
            </ul>
        </section>
    )
}
