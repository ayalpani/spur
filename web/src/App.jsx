const screens = [
  {
    src: '/screens/02-tracking.webp',
    number: '02',
    kicker: 'Im Hintergrund',
    title: 'Spur läuft. Du gehst.',
    copy: 'Die Tour bleibt auf der Karte sichtbar, während Spur Zeit, Wegpunkte und Strecke zuverlässig aufzeichnet – auch bei gesperrtem Bildschirm.',
    detail: 'Ein klarer Status, große Einhand-Bedienung und kein Fitness-Dashboard, das sich zwischen dich und deinen Weg stellt.',
    alt: 'Laufende Spur-Tour auf der Karte mit Dauer, Distanz und Wegpunktleiste',
    tone: 'green',
  },
  {
    src: '/screens/03-moments.webp',
    number: '03',
    kicker: 'Vier Arten zu erinnern',
    title: 'Nicht nur, wo du warst.',
    copy: 'Ein Foto, ein Video, eine Sprachnachricht oder ein Emoji: Momente landen genau an dem Ort, an dem sie passiert sind.',
    detail: 'Die Auswahl bleibt unten beim Daumen. Kein Formular, kein Feed, kein Umweg.',
    alt: 'Moment-Auswahl mit Foto, Video, Sprache und Emoji über der Spur-Karte',
    tone: 'sand',
  },
  {
    src: '/screens/04-emoji-moment.webp',
    number: '04',
    kicker: 'Am richtigen Ort',
    title: 'Erinnerungen bekommen einen Platz.',
    copy: 'Jeder Moment wird Teil der Karte. So erzählt ein Weg später mehr als eine Linie von A nach B.',
    detail: 'Marker, Medien und Notizen bleiben lokal auf dem Gerät und tauchen beim nächsten Öffnen wieder dort auf.',
    alt: 'Herz-Moment als Marker auf der Spur-Karte am Potsdamer Platz',
    tone: 'ink',
  },
  {
    src: '/screens/05-home.webp',
    number: '05',
    kicker: 'Dein Rückblick',
    title: 'Aus Wegen wird ein eigenes Archiv.',
    copy: 'Home fasst die letzten sieben Tage ruhig zusammen und hält jede Tour mit Zeit, Distanz und Kartenvorschau bereit.',
    detail: 'Keine Rangliste. Kein Vergleich. Nur deine Aktivität und die Orte, die für dich etwas bedeuten.',
    alt: 'Spur Home mit Sieben-Tage-Aktivität und lokalem Tourverlauf',
    tone: 'gray',
  },
  {
    src: '/screens/06-complete.webp',
    number: '06',
    kicker: 'Sicher angekommen',
    title: 'Der ganze Weg auf einen Blick.',
    copy: 'Nach dem Ankommen zeigt Spur die komplette Route und die wichtigsten Werte in einer einzigen, ruhigen Abschlussansicht.',
    detail: 'Danach wartet die Tour im lokalen Archiv – gemeinsam mit allen Momenten, die unterwegs entstanden sind.',
    alt: 'Abgeschlossene Spur-Tour mit Kartenübersicht, Zeit, Strecke und Tempo',
    tone: 'white',
  },
]

function SpurMark({ small = false }) {
  return (
    <svg
      className={small ? 'spur-mark spur-mark--small' : 'spur-mark'}
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

function PhoneFrame({ src, alt, tone = 'sand', eager = false }) {
  return (
    <figure className={`phone-scene phone-scene--${tone}`}>
      <div className="phone-frame">
        <span className="phone-button phone-button--volume" aria-hidden="true" />
        <span className="phone-button phone-button--power" aria-hidden="true" />
        <span className="phone-island" aria-hidden="true"><i /></span>
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

function Feature({ screen, index }) {
  return (
    <section className={`feature ${index % 2 ? 'feature--reverse' : ''}`}>
      <div className="feature-copy">
        <p className="eyebrow">{screen.number} · {screen.kicker}</p>
        <h2>{screen.title}</h2>
        <p className="feature-lead">{screen.copy}</p>
        <p className="feature-detail">{screen.detail}</p>
      </div>
      <PhoneFrame {...screen} />
    </section>
  )
}

function App() {
  return (
    <>
      <header className="site-header">
        <a className="brand" href="#top" aria-label="Spur – zum Seitenanfang">
          <SpurMark small />
          <span>Spur</span>
        </a>
        <nav aria-label="Hauptnavigation">
          <a href="#erleben">Erleben</a>
          <a href="#prinzipien">Prinzipien</a>
          <a className="nav-cta" href="#erleben">Sechs Einblicke</a>
        </nav>
      </header>

      <main>
        <section className="hero" id="top">
          <div className="hero-copy">
            <p className="eyebrow">Private Touren · Android</p>
            <h1>Wege, die<br />dir gehören.</h1>
            <p className="hero-lead">
              Spur zeichnet deine Touren auf und bewahrt Erinnerungen genau dort,
              wo sie entstanden sind. Lokal, ruhig und nur für dich.
            </p>
            <div className="hero-actions">
              <a className="primary-action" href="#erleben">Spur ansehen</a>
              <a className="text-action" href="#prinzipien">Warum local first?</a>
            </div>
            <ul className="hero-facts" aria-label="Spur Produktprinzipien">
              <li>Ohne Account</li>
              <li>Ohne Cloud</li>
              <li>Ohne Feed</li>
            </ul>
          </div>

          <div className="hero-visual" id="erleben">
            <PhoneFrame
              src="/screens/01-map.webp"
              alt="Spur-Karte mit Tour-starten-Aktion und einem gesetzten Herz-Moment"
              tone="green"
              eager
            />
            <div className="hero-caption">
              <span>01 · Die Karte zuerst</span>
              <strong>Ein Tipp. Und du bist unterwegs.</strong>
            </div>
          </div>
        </section>

        <section className="intro" aria-labelledby="intro-title">
          <p className="eyebrow">Ein privates Werkzeug</p>
          <h2 id="intro-title">Nicht für Leistung.<br />Für Erinnerung.</h2>
          <p>
            Spur beginnt auf der Karte, bleibt unterwegs aus dem Weg und bringt
            deine Tour später genauso zurück, wie sie war.
          </p>
        </section>

        <div className="showcase">
          {screens.map((screen, index) => (
            <Feature key={screen.src} screen={screen} index={index} />
          ))}
        </div>

        <section className="principles" id="prinzipien" aria-labelledby="principles-title">
          <div className="principles-heading">
            <p className="eyebrow">Local first, wirklich</p>
            <h2 id="principles-title">Deine Spur bleibt deine.</h2>
          </div>
          <div className="principle-grid">
            <article>
              <span>01</span>
              <h3>Alles auf dem Gerät</h3>
              <p>Touren, Standortpunkte und Momente werden lokal gespeichert.</p>
            </article>
            <article>
              <span>02</span>
              <h3>Keine Zuschauer</h3>
              <p>Kein Profil, keine Follower, kein öffentlicher Aktivitätsfeed.</p>
            </article>
            <article>
              <span>03</span>
              <h3>Du entscheidest</h3>
              <p>Daten verlassen das Gerät nur durch eine bewusste Aktion.</p>
            </article>
          </div>
        </section>
      </main>

      <footer>
        <div className="footer-mark"><SpurMark /></div>
        <p>Spur</p>
        <h2>Geh los.<br />Der Rest bleibt bei dir.</h2>
        <div className="footer-meta">
          <span>Android · In Entwicklung</span>
          <a href="#top">Zurück nach oben ↑</a>
        </div>
      </footer>
    </>
  )
}

export default App
