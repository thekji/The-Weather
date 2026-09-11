import { useEffect, useState, type ReactNode } from 'react'
import {
    automaticTheme,
    savedPreference,
    THEME_STORAGE_KEY,
    ThemeContext,
    type ThemePreference,
} from './theme'

export function ThemeProvider({ children }: { children: ReactNode }) {
    const [preference, setPreference] = useState<ThemePreference>(savedPreference)
    const [currentAutomaticTheme, setCurrentAutomaticTheme] = useState(automaticTheme)
    const resolvedTheme = preference === 'auto' ? currentAutomaticTheme : preference

    useEffect(() => {
        document.documentElement.dataset.timeTheme = resolvedTheme
        document.documentElement.dataset.themePreference = preference
        try {
            window.localStorage.setItem(THEME_STORAGE_KEY, preference)
        } catch {
            // The selected theme still works for this session if storage is unavailable.
        }
    }, [preference, resolvedTheme])

    useEffect(() => {
        if (preference !== 'auto') return

        const updateAutomaticTheme = () => setCurrentAutomaticTheme(automaticTheme())
        updateAutomaticTheme()
        const timer = window.setInterval(updateAutomaticTheme, 60_000)
        return () => window.clearInterval(timer)
    }, [preference])

    return (
        <ThemeContext.Provider value={{ preference, resolvedTheme, setPreference }}>
            {children}
        </ThemeContext.Provider>
    )
}
