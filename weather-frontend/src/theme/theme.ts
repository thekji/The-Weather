import { createContext, useContext } from 'react'

export type ThemePreference = 'auto' | 'day' | 'night'
export type ResolvedTheme = Exclude<ThemePreference, 'auto'>

export type ThemeContextValue = {
    preference: ThemePreference
    resolvedTheme: ResolvedTheme
    setPreference: (preference: ThemePreference) => void
}

export const THEME_STORAGE_KEY = 'weather-theme-preference'
export const ThemeContext = createContext<ThemeContextValue | null>(null)

export function automaticTheme(): ResolvedTheme {
    const hour = new Date().getHours()
    return hour >= 6 && hour < 18 ? 'day' : 'night'
}

export function savedPreference(): ThemePreference {
    try {
        const value = window.localStorage.getItem(THEME_STORAGE_KEY)
        return value === 'day' || value === 'night' || value === 'auto' ? value : 'auto'
    } catch {
        return 'auto'
    }
}

export function initializeTheme() {
    const preference = savedPreference()
    const resolvedTheme = preference === 'auto' ? automaticTheme() : preference
    document.documentElement.dataset.timeTheme = resolvedTheme
    document.documentElement.dataset.themePreference = preference
}

export function useTheme() {
    const context = useContext(ThemeContext)
    if (!context) throw new Error('useTheme must be used within ThemeProvider.')
    return context
}
