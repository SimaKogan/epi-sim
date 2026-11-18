import { COLORS } from '../i18n.js';

const ITEMS = [
  { i: 0, key: 'sS', color: COLORS.S },
  { i: 1, key: 'sV', color: COLORS.V },
  { i: 2, key: 'sE', color: COLORS.E },
  { i: 3, key: 'sI', color: COLORS.I },
  { i: 4, key: 'sR', color: COLORS.R },
  { i: 5, key: 'sD', color: COLORS.D },
];

export default function StatCards({ frame, t }) {
  const counts = frame ? frame.counts : [0, 0, 0, 0, 0, 0];
  const st = frame ? frame.stats : null;
  const pop = st ? st.population : 1;
  const pct = (v) => `${((v / pop) * 100).toFixed(v / pop < 0.1 ? 1 : 0)}%`;
  return (
    <div className="stats">
      <div className="cards">
        {ITEMS.map((it) => (
          <div className="card" key={it.key} style={{ '--c': it.color }}>
            <span className="dot" />
            <div className="card-label">{t(it.key)}</div>
            <div className="card-value">{counts[it.i].toLocaleString()}</div>
            <div className="card-sub">{pct(counts[it.i])}</div>
          </div>
        ))}
      </div>
      <div className="kpis">
        <div className="kpi"><span>{t('rt')}</span><b className={st && st.rt != null ? (st.rt > 1 ? 'bad' : 'good') : ''}>{st && st.rt != null ? st.rt.toFixed(2) : '—'}</b></div>
        <div className="kpi"><span>{t('peak')}</span><b>{st ? st.peak.toLocaleString() : '—'}</b><small>{st ? t('peakDay', Math.round(st.peakDay)) : ''}</small></div>
        <div className="kpi"><span>{t('everInfected')}</span><b>{st ? pct(st.everInfected) : '—'}</b></div>
        <div className="kpi"><span>{t('vaccinatedPct')}</span><b>{st ? pct(st.vaccinated) : '—'}</b></div>
      </div>
    </div>
  );
}
