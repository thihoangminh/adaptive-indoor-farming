
import { useEffect, useState } from 'react'
import './App.css'


type Weather = 'SUNNY' | 'CLOUDY' | 'RAINY'
type SimulationStep = {
  hour: number
  naturalPpfd: number
  sunSpectrum: number[]
  adaptiveLedFraction: number
  adaptiveLedPpfd: number
  adaptivePowerWatts: number
  adaptiveAccumulatedDli: number
  adaptiveLedSpectrum: number[]
  decisionReason: string
  fixedLedPpfd: number
}


type SimulationResult = {
  weather: Weather
  targetDli: number
  adaptive: {
    energyKWh: number
    cost: number
    dli: number
    dliGap: number
    targetReached: boolean
  }
  fixed: {
    energyKWh: number
    dli: number
  }
  comparison: {
    kWhSaved: number
    kWhSavedPercent: number
    costSaved: number
  }
  steps: SimulationStep[]
}
// const weatherData = {
//   SUNNY:  { sunlight: 620, led: 0,     energy: 0.00, dli: 22.46 },
//   CLOUDY: { sunlight: 290, led: 180,   energy: 0.962, dli: 17.00 },
//   RAINY:  { sunlight: 110, led: 260,   energy: 1.560, dli: 17.00 },
// }

function App() {
  
  const [weather, setWeather] = useState<Weather>('CLOUDY')
  const [hour, setHour] = useState(12)

  const [simulation, setSimulation] = useState<SimulationResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch(`http://localhost:8080/api/simulation?weather=${weather}`)
      .then(response => {
        if (!response.ok) {
          throw new Error('Failed to retrieve simulation')
        }
        return response.json()
      })
      .then((result: SimulationResult) => {
        setSimulation(result)
        setError(null)
      })
      .catch(err => {
        setError(err.message)
      })
  }, [weather])

  const currentStep = simulation?.steps.reduce((previous, current) =>
    Math.abs(current.hour - hour) < Math.abs(previous.hour - hour)
      ? current
      : previous
  )
  const sunSpectrum = currentStep?.sunSpectrum ?? []
  const ledSpectrum = currentStep?.adaptiveLedSpectrum ?? []
  const data = simulation ? {
    sunlight: currentStep?.naturalPpfd ?? 0,
    led: currentStep?.adaptiveLedPpfd ?? 0,
    energy: simulation.adaptive.energyKWh,
    dli: simulation.adaptive.dli,
  } : null

  return (
    <main className="dashboard">
      {error && <p>API error: {error}</p>}
      {!simulation && !error && <p>Loading simulation...</p>}

      {data && simulation && (
        <>
        <header className="topbar">
        <div className="brand">
          <span className="brand-icon">✳</span>
          <span>VERDANT <small> / CONTROL SYSTEMS</small></span>
        </div>
        <span className="status"><i /> SIMULATION MODE</span>
      </header>

      <section className="intro">
        <p className="eyebrow">RESEARCH PROJECT / 001</p>
        <h1>Growing smarter.</h1>
        <p className="subtitle">
          Adaptive lighting control for indoor agriculture.
          Balance plant light requirements with energy consumption.
        </p>
      </section>
      <section className="time-control">
        <div className="time-heading">
          <p className="eyebrow">TIME SIMULATION</p>
          <h2>{hour}:00</h2>
        </div>

        <input
          type="range"
          min="6"
          max="22"
          step="1"
          value={hour}
          onChange={(event) => setHour(Number(event.target.value))}
        />

        <div className="time-labels">
          <span>6:00 AM</span>
          <span>2:00 PM</span>
          <span>10:00 PM</span>
        </div>
      </section>
      <section className="toolbar">
        <div>
          <p className="eyebrow">ENVIRONMENT</p>
          <h2>Weather conditions</h2>
        </div>
        <div className="weather-buttons">
          {(['SUNNY', 'CLOUDY', 'RAINY'] as Weather[]).map((option) => (
            <button
              key={option}
              className={weather === option ? 'selected' : ''}
              onClick={() => setWeather(option)}
            >
              {option === 'SUNNY' ? '☀' : option === 'CLOUDY' ? '☁' : '☂'}
              {' '}{option}
            </button>
          ))}
        </div>
      </section>

      <section className="metrics">
        <article className="metric-card">
          <p className="eyebrow">NATURAL LIGHT</p>
          <strong>{data.sunlight}<span> PPFD</span></strong>
          <p>Incoming sunlight intensity</p>
        </article>
        <article className="metric-card">
          <p className="eyebrow">LED OUTPUT</p>
          <strong>{data.led}<span> PPFD</span></strong>
          <p>Supplemental light intensity</p>
        </article>
        <article className="metric-card">
          <p className="eyebrow">DAILY LIGHT INTEGRAL</p>
          <strong>{data.dli.toFixed(2)}<span> mol/m²</span></strong>
          <p>Target: 17.00 mol/m²/day</p>
        </article>
        <article className="metric-card accent-card">
          <p className="eyebrow">ADAPTIVE ENERGY</p>
          <strong>{data.energy.toFixed(3)}<span> kWh</span></strong>
          <p>LED electricity per simulated day</p>
        </article>
      </section>

      <section className="chart-grid">
        <article className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">SPECTRAL ANALYSIS</p>
              <h2>Sunlight spectrum</h2>
            </div>
            <span className="legend sunlight-legend">Sunlight</span>
          </div>
          <div className="chart">
            <svg viewBox="0 0 600 260" className="spectrum-chart">
              {/* Grid lines */}
              {[50, 100, 150, 200].map(y => (
                <line
                  key={y}
                  x1="55"
                  y1={y}
                  x2="580"
                  y2={y}
                  stroke="currentColor"
                  opacity="0.12"
                />
              ))}

              {/* Sunlight spectrum */}
              <polyline
                points={sunSpectrum
                  .map((value, index) =>
                    `${55 + index * (525 / 15)},${210 - value * 90}`
                  )
                  .join(' ')}
                fill="none"
                stroke="#8da779"
                strokeWidth="3"
                strokeLinejoin="round"
              />

              {/* Adaptive LED spectrum */}
              <polyline
                points={ledSpectrum
                  .map((value, index) =>
                    `${55 + index * (525 / 15)},${210 - value * 90}`
                  )
                  .join(' ')}
                fill="none"
                stroke="#d49b55"
                strokeWidth="3"
                strokeLinejoin="round"
              />

              {/* Wavelength labels */}
              {[400, 500, 600, 700].map((wavelength, index) => (
                <text
                  key={wavelength}
                  x={55 + index * 175}
                  y="235"
                  textAnchor="middle"
                  fill="currentColor"
                  fontSize="12"
                >
                  {wavelength} nm
                </text>
              ))}
            </svg>
          </div>
          <p className="chart-note">
            Illustrative spectrum · {weather.toLowerCase()} preset
          </p>
        </article>

        <article className="panel controller-panel">
          <p className="eyebrow">CONTROL LOGIC</p>
          <h2>Controller reasoning</h2>
          <div className="decision">
            <span className="decision-dot" />
            <div>
              <strong>
                {currentStep?.decisionReason ?? 'Waiting for simulation'}
              </strong>

              <p>
                {currentStep?.decisionReason ??
                  'The controller is evaluating available natural light.'}
              </p>
            </div>
          </div>
          <div className="decision-stats">
            <div><span>Weather</span><strong>{weather}</strong></div>
            <div><span>Daily target</span><strong>17.00 mol/m²</strong></div>
            <div><span>Control mode</span><strong>Adaptive</strong></div>
          </div>
        </article>
      </section>

      <footer>
        <span>VERDANT / ADAPTIVE LIGHTING RESEARCH</span>
        <span>PROTOTYPE V0.1 · SAMPLE DATA</span>
      </footer>
      </>
      )}
    
    </main>
  )
}

export default App
