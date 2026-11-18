import { formatLog } from '../i18n.js';

export default function EventLog({ log, t }) {
  const items = (log || []).slice().reverse().slice(0, 14);
  return (
    <section className="panel">
      <h3>{t('events')}</h3>
      {items.length === 0 ? <p className="muted">{t('noEvents')}</p> : (
        <ul className="log">
          {items.map((e, i) => (
            <li key={`${e.day}-${i}-${e.type}`}>
              <span className="log-day">{Math.floor(e.day)}</span>
              <span>{formatLog(t, e)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
