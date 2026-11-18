import { useMemo, useState } from 'react';
import { useSimulation } from './useSimulation.js';
import { makeT, COLORS } from './i18n.js';
import { post } from './api.js';
import MapCanvas from './components/MapCanvas.jsx';
import ChartCanvas from './components/ChartCanvas.jsx';
import Controls from './components/Controls.jsx';
import StatCards from './components/StatCards.jsx';
import VariantList from './components/VariantList.jsx';
import EventLog from './components/EventLog.jsx';

function initialLang() {
  try {
    const saved = localStorage.getItem('episim.lang');
    if (saved === 'en' || saved === 'ru') return saved;
  } catch { /* storage unavailable */ }
  return (navigator.language || 'en').toLowerCase().startsWith('ru') ? 'ru' : 'en';
}

export default function App() {
  const { simRef, ui, connected } = useSimulation();
  const [lang, setLang] = useState(initialLang);
  const [showReset, setShowReset] = useState(false);
  const [population, setPopulation] = useState(4000);
  const [initial, setInitial] = useState(5);
  const t = useMemo(() => makeT(lang), [lang]);

  const frame = ui.frame;
  const status = !connected ? 'connecting' : frame ? frame.status : 'connecting';
  const history = simRef.current.history;

  const switchLang = () => {
    const next = lang === 'en' ? 'ru' : 'en';
    setLang(next);
    try { localStorage.setItem('episim.lang', next); } catch { /* ignore */ }
    document.documentElement.lang = next;
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <svg width="30" height="30" viewBox="0 0 32 32" aria-hidden="true">
            <circle cx="16" cy="16" r="6" fill={COLORS.I} />
            <circle cx="16" cy="16" r="11" fill="none" stroke={COLORS.I} strokeWidth="1.6" opacity=".5" />
            <circle cx="16" cy="16" r="15" fill="none" stroke={COLORS.I} strokeWidth="1" opacity=".22" />
          </svg>
          <div>
            <h1>EpiSim</h1>
            <p>{t('subtitle')}</p>
          </div>
        </div>
        <div className="top-actions">
          <span className={`status ${status}`}><i />{t(status)}</span>
          <span className="daybadge">{t('day')} <b>{frame ? Math.floor(frame.day) : 0}</b></span>
          <button className="btn" onClick={() => post(status === 'paused' ? 'resume' : 'pause')} disabled={!connected}>
            {status === 'paused' ? t('resume') : t('pause')}
          </button>
          <button className="btn primary" onClick={() => setShowReset((v) => !v)}>{t('reset')}</button>
          <button className="btn ghost" onClick={switchLang} aria-label="Language">{lang === 'en' ? 'RU' : 'EN'}</button>
        </div>
      </header>

      {showReset && (
        <div className="reset-pop">
          <label>{t('population')}: <b>{population.toLocaleString()}</b>
            <input type="range" min="1000" max="6000" step="500" value={population}
              style={{ '--pct': `${((population - 1000) / 5000) * 100}%` }}
              onChange={(e) => setPopulation(parseInt(e.target.value, 10))} />
          </label>
          <label>{t('initialCases')}: <b>{initial}</b>
            <input type="range" min="1" max="40" step="1" value={initial}
              style={{ '--pct': `${((initial - 1) / 39) * 100}%` }}
              onChange={(e) => setInitial(parseInt(e.target.value, 10))} />
          </label>
          <div className="reset-actions">
            <button className="btn" onClick={() => setShowReset(false)}>{t('cancel')}</button>
            <button className="btn primary" onClick={() => { post('reset', { population, initialInfected: initial }); setShowReset(false); }}>{t('start')}</button>
          </div>
        </div>
      )}

      {!connected && <div className="offline">{t('offline')}</div>}

      <main className="layout">
        <div className="main-col">
          <section className="panel map-panel">
            <MapCanvas simRef={simRef} hint={t('mapHint')} onSeed={(x, y) => post('infect', { x, y, count: 5 })} />
          </section>
          <StatCards frame={frame} t={t} />
          <section className="panel chart-panel">
            <div className="chart-head">
              <h3>{t('chartTitle')}</h3>
              <ul className="legend">
                {[['sS', COLORS.S], ['sV', COLORS.V], ['sE', COLORS.E], ['sI', COLORS.I], ['sR', COLORS.R], ['sD', COLORS.D]].map(([k, c]) => (
                  <li key={k}><i style={{ background: c }} />{t(k)}</li>
                ))}
              </ul>
            </div>
            <ChartCanvas history={history} version={ui.version} mode="stack" height={210}
              population={frame ? frame.stats.population : 4000} t={t} />
            <h3 className="sub">{t('rtTitle')}</h3>
            <ChartCanvas history={history} version={ui.version} mode="rt" height={120} t={t} />
          </section>
        </div>
        <aside className="side-col">
          <Controls serverParams={frame ? frame.params : null} t={t} />
          <VariantList variants={frame ? frame.variants : []} t={t} />
          <EventLog log={ui.log} t={t} />
        </aside>
      </main>

      <footer className="footer">© {new Date().getFullYear()} Sima Kogan · MIT · <a href="https://github.com/SimaKogan" target="_blank" rel="noreferrer">GitHub</a></footer>
    </div>
  );
}
