const screens = [
  {
    src: '/screens/01-map.webp',
    title: 'Die Karte zuerst',
    copy: 'Ein Tipp auf der Karte, und du bist unterwegs.',
    alt: 'Spur-Karte mit Tour-starten-Aktion und einem gesetzten Herz-Moment',
  },
  {
    src: '/screens/02-tracking.webp',
    title: 'Unterwegs',
    copy: 'Zeit und Strecke bleiben sichtbar, ohne dich abzulenken.',
    alt: 'Laufende Spur-Tour auf der Karte mit Dauer, Distanz und Wegpunktleiste',
  },
  {
    src: '/screens/03-moments.webp',
    title: 'Momente festhalten',
    copy: 'Foto, Video, Stimme oder Emoji bewahren den Moment am richtigen Ort.',
    alt: 'Moment-Auswahl mit Foto, Video, Sprache und Emoji über der Spur-Karte',
  },
  {
    src: '/screens/04-emoji-moment.webp',
    title: 'Erinnerungen verorten',
    copy: 'Jede Erinnerung bleibt genau dort, wo sie passiert ist.',
    alt: 'Herz-Moment als Marker auf der Spur-Karte am Potsdamer Platz',
  },
  {
    src: '/screens/05-home.webp',
    title: 'Dein Rückblick',
    copy: 'Deine Touren werden zum Rückblick – nicht zur Rangliste.',
    alt: 'Spur Home mit Sieben-Tage-Aktivität und lokalem Tourverlauf',
  },
  {
    src: '/screens/06-complete.webp',
    title: 'Sicher angekommen',
    copy: 'Der ganze Weg und seine wichtigsten Momente auf einen Blick.',
    alt: 'Abgeschlossene Spur-Tour mit Kartenübersicht, Zeit, Strecke und Tempo',
  },
]

function SpurMark() {
  return (
    <svg
      className="spur-mark"
      viewBox="0 0 64 64"
      aria-hidden="true"
    >
      <g stroke="currentColor" strokeWidth="8" strokeLinecap="round">
        <path d="M32 10v44" />
        <path d="m13 21 38 22" />
        <path d="m51 21-38 22" />
      </g>
    </svg>
  )
}

function PhoneFrame({ src, alt, eager = false }) {
  return (
    <figure className="phone-scene">
      <div className="phone-frame">
        <span className="phone-button phone-button--volume" aria-hidden="true" />
        <span className="phone-button phone-button--power" aria-hidden="true" />
        <span className="phone-notch" aria-hidden="true"><i /></span>
        <div className="phone-screen">
          <img
            src={src}
            alt={alt}
            width="1080"
            height="2400"
            loading={eager ? 'eager' : 'lazy'}
            fetchPriority={eager ? 'high' : 'auto'}
          />
        </div>
      </div>
    </figure>
  )
}

function ScreenCard({ screen, eager = false }) {
  return (
    <article className="screen-card">
      <PhoneFrame {...screen} eager={eager} />
      <div className="screen-copy">
        <h2>{screen.title}</h2>
        <p>{screen.copy}</p>
      </div>
    </article>
  )
}

function App() {
  return (
    <>
      <header className="site-header">
        <a className="brand" href="#top" aria-label="Spur – zum Seitenanfang">
          <SpurMark />
          <span>Spur</span>
        </a>
        <nav aria-label="Hauptnavigation">
          <a className="nav-cta" href="#screens">Sechs Einblicke</a>
        </nav>
      </header>

      <main>
        <section className="hero" id="top">
          <p className="eyebrow">Persönlich statt öffentlich</p>
          <h1>Wege, die dir gehören.</h1>
          <p className="hero-lead">
            Spur bewahrt deine Touren und Erinnerungen für dich. Nur du
            entscheidest, wer sie sehen darf.
          </p>
        </section>

        <section className="screens" id="screens" aria-labelledby="screens-title">
          <h2 className="section-heading" id="screens-title">Spur in sechs Screens.</h2>
          <div className="screen-grid">
            {screens.map((screen, index) => (
              <ScreenCard key={screen.src} screen={screen} eager={index < 3} />
            ))}
          </div>
        </section>
      </main>

      <footer>
        <h2>Deine Wege bleiben privat.</h2>
        <p>Nur du entscheidest, wer deine Touren und Erinnerungen sieht.</p>
      </footer>
    </>
  )
}

export default App
