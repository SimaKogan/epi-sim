import { useEffect, useRef, useState } from 'react';
import { post } from '../api.js';

const SLIDERS = [
  { group: 'vaccination', items: [
    { key: 'vaccinationRate', label: 'vaccinationRate', min: 0, max: 3, step: 0.1, unit: 'perDay', desc: 'd_vaccination', fmt: (v) => v.toFixed(1) },
  ] },
  { group: 'mutation', items: [
    { key: 'mutationRate', label: 'mutationRate', min: 0, max: 1, step: 0.05, desc: 'd_mutation', fmt: (v) => `${Math.round(v * 100)}%` },
  ] },
  { group: 'restrictions', items: [
    { key: 'lockdown', label: 'lockdown', min: 0, max: 1, step: 0.05, desc: 'd_lockdown', fmt: (v) => `${Math.round(v * 100)}%` },
    { key: 'masks', label: 'masks', min: 0, max: 1, step: 0.05, desc: 'd_masks', fmt: (v) => `${Math.round(v * 100)}%` },
    { key: 'testing', label: 'testing', min: 0, max: 1, step: 0.05, desc: 'd_testing', fmt: (v) => `${Math.round(v * 100)}%` },
  ] },
  { group: 'disease', items: [
    { key: 'infectiousness', label: 'infectiousness', min: 0.3, max: 3, step: 0.1, desc: 'd_infectiousness', fmt: (v) => `×${v.toFixed(1)}` },
    { key: 'contactRadius', label: 'contactRadius', min: 4, max: 20, step: 1, desc: 'd_contactRadius', fmt: (v) => String(Math.round(v)) },
    { key: 'infectiousDays', label: 'infectiousDays', min: 3, max: 14, step: 1, desc: 'd_infectiousDays', unit: 'days', fmt: (v) => String(Math.round(v)) },
    { key: 'fatality', label: 'fatality', min: 0, max: 10, step: 0.1, desc: 'd_fatality', fmt: (v) => `${v.toFixed(1)}%` },
    { key: 'vaccineEfficacy', label: 'vaccineEfficacy', min: 0, max: 1, step: 0.05, desc: 'd_vaccineEfficacy', fmt: (v) => `${Math.round(v * 100)}%` },
  ] },
  { group: 'simulation', items: [
    { key: 'speed', label: 'speed', min: 0.5, max: 10, step: 0.5, unit: 'speedUnit', fmt: (v) => v.toFixed(1) },
  ] },
];

const PRESETS = [
  { label: 'p_free', values: { vaccinationRate: 0, lockdown: 0, masks: 0, testing: 0 } },
  { label: 'p_soft', values: { vaccinationRate: 0, lockdown: 0, masks: 0.7, testing: 0.6 } },
  { label: 'p_lock', values: { vaccinationRate: 0, lockdown: 0.75, masks: 0.3, testing: 0.3 } },
  { label: 'p_vax', values: { vaccinationRate: 2, lockdown: 0, masks: 0.3, testing: 0.3 } },
];

export default function Controls({ serverParams, t }) {
  const [values, setValues] = useState(null);
  const timer = useRef(null);
  const pending = useRef({});

  // adopt the server's values once (afterwards the sliders are the source of truth)
  useEffect(() => {
    if (!values && serverParams) setValues(serverParams);
  }, [serverParams, values]);

  const push = (patch) => {
    pending.current = { ...pending.current, ...patch };
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      post('params', pending.current);
      pending.current = {};
    }, 70);
  };

  const change = (key, v) => {
    setValues((cur) => ({ ...cur, [key]: v }));
    push({ [key]: v });
  };

  const applyPreset = (p) => {
    setValues((cur) => ({ ...cur, ...p.values }));
    push(p.values);
  };

  if (!values) return <section className="panel"><div className="muted">…</div></section>;

  return (
    <section className="panel controls">
      <div className="presets">
        <span className="label">{t('presets')}</span>
        <div className="preset-row">
          {PRESETS.map((p) => (
            <button key={p.label} className="chip" onClick={() => applyPreset(p)}>{t(p.label)}</button>
          ))}
        </div>
      </div>
      {SLIDERS.map((g) => (
        <div className="group" key={g.group}>
          <h3>{t(g.group)}</h3>
          {g.items.map((s) => {
            const v = values[s.key] ?? 0;
            const pct = ((v - s.min) / (s.max - s.min)) * 100;
            return (
              <label className="slider" key={s.key} title={s.desc ? t(s.desc) : undefined}>
                <div className="slider-head">
                  <span>{t(s.label)}</span>
                  <output>{s.fmt(v)}{s.unit ? <small> {t(s.unit)}</small> : null}</output>
                </div>
                <input
                  type="range" min={s.min} max={s.max} step={s.step} value={v}
                  style={{ '--pct': `${pct}%` }}
                  onChange={(e) => change(s.key, parseFloat(e.target.value))}
                />
                {s.desc ? <p className="desc">{t(s.desc)}</p> : null}
              </label>
            );
          })}
        </div>
      ))}
    </section>
  );
}
