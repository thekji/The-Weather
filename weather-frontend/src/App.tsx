import './App.css'
import RouteConfig from './config/RouteConfig'
import { ThemeProvider } from './theme/ThemeProvider'

function App() {
    return <ThemeProvider><RouteConfig /></ThemeProvider>
}

export default App
