import { useEffect, useRef, useState } from 'react';
import { COLORS } from '../i18n.js';

// stacking order, bottom → top: columns of a history row are [day, S, V, E, I, R, D, rt]
const STACK = [
  { idx: 4, color: COLORS.I, key: 'sI' },
  { idx: 3, color: COLORS.E, key: 'sE' },
  { idx: 6, color: COLORS.D, key: 'sD' },
  { idx: 5, color: COLORS.R, key: 'sR' },
  { idx: 2, color: COLORS.V, key: 'sV' },
  { idx: 1, color: COLORS.S, key: 'sS' },
];

/**
 * Canvas chart over the daily history.
 *  mode="stack": stacked area of all compartments
 *  mode="rt":    line of the reproduction number with the Rt = 1 threshold
 */
export default function ChartCanvas({ history, version, mode, population, height, t }) {
  const ref = useRef(null);
  const wrapRef = useRef(null);
  const [hover, setHover] = useState(null);
  const [size, setSize] = useState(0);

  useEffect(() => {
    const ro = new ResizeObserver(() => setSize(wrapRef.current.clientWidth));
    ro.observe(wrapRef.current);
    setSize(wrapRef.current.clientWidth);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas || !size) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = size, h = height;
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
    canvas.style.height = `${h}px`;
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    const pad = { l: 44, r: 12, t: 10, b: 24 };
    const pw = w - pad.l - pad.r, ph = h - pad.t - pad.b;
    const rows = history;
    const maxDay = Math.max(30, rows.length ? rows[rows.length - 1][0] : 0);
    const X = (d) => pad.l + (d / maxDay) * pw;

    let yMax = population || 1;
    if (mode === 'rt') {
      yMax = 4;
      for (const r of rows) if (r[7] > yMax) yMax = Math.ceil(r[7]);
    }
    const Y = (v) => pad.t + ph - (v / yMax) * ph;

    // grid + axes
    ctx.font = '11px Inter, system-ui, sans-serif';
    ctx.fillStyle = 'rgba(136,147,176,0.95)';
    ctx.strokeStyle = 'rgba(120,140,200,0.14)';
    ctx.lineWidth = 1;
    ctx.textAlign = 'right';
    const yTicks = 4;
    for (let k = 0; k <= yTicks; k++) {
      const v = (yMax * k) / yTicks;
      const y = Y(v);
      ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(w - pad.r, y); ctx.stroke();
      ctx.fillText(mode === 'rt' ? v.toFixed(1) : Math.round(v).toLocaleString(), pad.l - 6, y + 4);
    }
    ctx.textAlign = 'center';
    const xStep = maxDay > 240 ? 60 : maxDay > 120 ? 30 : 10;
    for (let d = 0; d <= maxDay; d += xStep) {
      ctx.fillText(String(d), X(d), h - 7);
    }

    if (rows.length > 1) {
      if (mode === 'stack') {
        // cumulative baselines per row
        const base = rows.map(() => 0);
        for (const layer of STACK) {
          const top = rows.map((r, i) => base[i] + r[layer.idx]);
          ctx.beginPath();
          rows.forEach((r, i) => { const x = X(r[0]), y = Y(top[i]); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
          for (let i = rows.length - 1; i >= 0; i--) ctx.lineTo(X(rows[i][0]), Y(base[i]));
          ctx.closePath();
          ctx.fillStyle = layer.color + (layer.idx === 4 ? 'ee' : 'aa');
          ctx.fill();
          base.splice(0, base.length, ...top);
        }
      } else {
        // Rt line
        ctx.strokeStyle = 'rgba(255,255,255,0.35)';
        ctx.setLineDash([4, 5]);
        ctx.beginPath(); ctx.moveTo(pad.l, Y(1)); ctx.lineTo(w - pad.r, Y(1)); ctx.stroke();
        ctx.setLineDash([]);
        ctx.strokeStyle = COLORS.E;
        ctx.lineWidth = 2;
        ctx.lineJoin = 'round';
        ctx.beginPath();
        let started = false;
        for (const r of rows) {
          if (r[7] < 0) { started = false; continue; }
          const x = X(r[0]), y = Y(r[7]);
          started ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
          started = true;
        }
        ctx.stroke();
      }
    }

    // hover crosshair
    if (hover !== null && rows.length) {
      const day = Math.max(0, Math.min(maxDay, ((hover - pad.l) / pw) * maxDay));
      let best = rows[0];
      for (const r of rows) if (Math.abs(r[0] - day) < Math.abs(best[0] - day)) best = r;
      ctx.strokeStyle = 'rgba(255,255,255,0.5)';
      ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(X(best[0]), pad.t); ctx.lineTo(X(best[0]), pad.t + ph); ctx.stroke();
    }
  }, [history, version, mode, population, height, size, hover]);

  const onMove = (e) => {
    const rect = ref.current.getBoundingClientRect();
    setHover(e.clientX - rect.left);
  };

  // tooltip content
  let tip = null;
  if (hover !== null && history.length) {
    const pad = { l: 44, r: 12 };
    const pw = size - pad.l - pad.r;
    const maxDay = Math.max(30, history[history.length - 1][0]);
    const day = ((hover - pad.l) / pw) * maxDay;
    let best = history[0];
    for (const r of history) if (Math.abs(r[0] - day) < Math.abs(best[0] - day)) best = r;
    const left = Math.min(size - 150, Math.max(4, pad.l + (best[0] / maxDay) * pw + 10));
    tip = (
      <div className="chart-tip" style={{ left }}>
        <b>{t('tooltipDay', Math.floor(best[0]))}</b>
        {mode === 'rt' ? (
          <div>Rt: {best[7] < 0 ? '—' : best[7].toFixed(2)}</div>
        ) : STACK.slice().reverse().map((l) => (
          <div key={l.key}><i style={{ background: l.color }} />{t(l.key)}: {best[l.idx].toLocaleString()}</div>
        ))}
      </div>
    );
  }

  return (
    <div className="chart-wrap" ref={wrapRef} onMouseMove={onMove} onMouseLeave={() => setHover(null)}>
      <canvas ref={ref} />
      {tip}
    </div>
  );
}
