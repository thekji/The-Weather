import sunWeatherIcon from '../assets/icons/weather-partly-cloudy-day.svg'

export function WeatherBrandIcon() {
    return (
        <img
            className="block h-full w-full object-contain"
            src={sunWeatherIcon}
            alt=""
            aria-hidden="true"
        />
    )
}
