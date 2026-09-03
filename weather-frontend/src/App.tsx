import { useEffect, useState } from 'react'
import './App.css'

type DeploymentResponse = {
  message: string
}

function App() {
  const [message, setMessage] = useState('Loading deployment message...')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    async function loadMessage() {
      try {
        const response = await fetch('/api/weather', {
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        })

        if (!response.ok) {
          throw new Error(`Backend returned ${response.status}`)
        }

        const data = await response.json() as DeploymentResponse
        setMessage(data.message)
      } catch (requestError) {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        setError(requestError instanceof Error ? requestError.message : 'Could not load backend message')
      }
    }

    void loadMessage()

    return () => controller.abort()
  }, [])

  return (
    <main className="deployment-page">
      <section className="message-panel" aria-live="polite">
        <p className="eyebrow">ECS deployment test</p>
        <h1>{error ?? message}</h1>
      </section>
    </main>
  )
}

export default App
