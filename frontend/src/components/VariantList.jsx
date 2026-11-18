import { VARIANT_COLORS } from '../i18n.js';

export default function VariantList({ variants, t }) {
  const list = variants || [];
  const maxActive = Math.max(1, ...list.map((v) => v.active));
  return (
    <section className="panel">
      <h3>{t('variants')}</h3>
      <ul className="variants">
        {list.map((v) => (
          <li key={v.id} className="variant" style={{ '--c': VARIANT_COLORS[v.id % VARIANT_COLORS.length] }}>
            <div className="variant-head">
              <span className="vname"><i />{v.name}</span>
              <span className="muted">{v.id === 0 ? '' : `${t('born', Math.round(v.born))} · `}{v.active.toLocaleString()} {t('active')}</span>
            </div>
            <div className="bar"><div style={{ width: `${(v.active / maxActive) * 100}%` }} /></div>
            <div className="vmeta">
              <span>{t('beta')} ×{v.beta.toFixed(2)}</span>
              <span>{t('escape')} {Math.round(v.escape * 100)}%</span>
              <span>{t('lethality')} ×{v.ifr.toFixed(2)}</span>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
