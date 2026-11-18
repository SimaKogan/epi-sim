import { useEffect, useRef } from 'react';
import { COLORS } from '../i18n.js';

const CODE_COLORS = [COLORS.S, COLORS.V, COLORS.E, COLORS.I, COLORS.R, COLORS.D];

function hexToRgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** Heat colour of a settlement: calm teal → amber → alarm red, by share of infectious people. */
function heat(share) {
  const k = Math.min(1, share / 0.2);
  const a = hexToRgb('#1fb6a6'), b = hexToRgb('#fbbf24'), c = hexToRgb('#ff4d6d');
  const [from, to, t] = k < 0.5 ? [a, b, k * 2] : [b, c, (k - 0.5) * 2];
  return from.map((v, i) => Math.round(v + (to[i] - v) * t));
}

/**
 * Live map. Agent positions are interpolated between the two latest frames so motion stays
 * smooth at 60 fps although the backend sends ~30 frames per second.
 */
export default function MapCanvas({ simRef, hint, onSeed }) {
  const canvasRef = useRef(null);
  const wrapRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const wrap = wrapRef.current;
    const ctx = canvas.getContext('2d');
    let raf = 0;
    let w = 0, h = 0, dpr = 1;

    const resize = () => {
      const world = simRef.current.world;
      const ratio = world ? world.height / world.width : 0.6;
      w = wrap.clientWidth;
      h = Math.round(w * ratio);
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      canvas.style.height = `${h}px`;
    };
    const ro = new ResizeObserver(resize);
    ro.observe(wrap);
    resize();

    const draw = (now) => {
      raf = requestAnimationFrame(draw);
      const s = simRef.current;
      const world = s.world;
      if (!world) { return; }
      if (Math.abs(h - w * world.height / world.width) > 2) resize();

      const sc = (w * dpr) / world.width;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.globalCompositeOperation = 'source-over';
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      // background: soft vignette + dotted grid
      const bg = ctx.createRadialGradient(canvas.width / 2, canvas.height / 2, 40, canvas.width / 2, canvas.height / 2, canvas.width * 0.7);
      bg.addColorStop(0, '#0f1730'); bg.addColorStop(1, '#070b14');
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = 'rgba(120,140,200,0.10)';
      const step = 40 * sc;
      for (let gx = step / 2; gx < canvas.width; gx += step) {
        for (let gy = step / 2; gy < canvas.height; gy += step) ctx.fillRect(gx, gy, 1.5 * dpr, 1.5 * dpr);
      }

      ctx.setTransform(sc, 0, 0, sc, 0, 0);
      const frame = s.frame;

      // routes
      ctx.strokeStyle = 'rgba(140,160,220,0.22)';
      ctx.lineWidth = 1.2;
      ctx.setLineDash([5, 7]);
      ctx.lineDashOffset = -(now / 80) % 12;
      for (const [a, b] of world.links) {
        const A = world.settlements[a], B = world.settlements[b];
        ctx.beginPath(); ctx.moveTo(A.x, A.y); ctx.lineTo(B.x, B.y); ctx.stroke();
      }
      ctx.setLineDash([]);

      // settlements
      world.settlements.forEach((st, i) => {
        const infected = frame ? frame.settlements[i] : 0;
        const [r, g, b] = heat(infected / st.pop);
        const grad = ctx.createRadialGradient(st.x, st.y, st.r * 0.2, st.x, st.y, st.r * 1.25);
        grad.addColorStop(0, `rgba(${r},${g},${b},0.16)`);
        grad.addColorStop(0.75, `rgba(${r},${g},${b},0.07)`);
        grad.addColorStop(1, `rgba(${r},${g},${b},0)`);
        ctx.fillStyle = grad;
        ctx.beginPath(); ctx.arc(st.x, st.y, st.r * 1.25, 0, Math.PI * 2); ctx.fill();
        ctx.strokeStyle = `rgba(${r},${g},${b},0.55)`;
        ctx.lineWidth = 1.5;
        ctx.beginPath(); ctx.arc(st.x, st.y, st.r, 0, Math.PI * 2); ctx.stroke();
      });

      // agents
      if (s.curr) {
        const a0 = s.prev, a1 = s.curr;
        const span = Math.max(16, s.currTime - s.prevTime);
        const t = Math.max(0, Math.min(1, (now - s.currTime) / span));
        const n = a1.length / 5;
        const groups = [[], [], [], [], [], []];
        for (let i = 0; i < n; i++) {
          const o = i * 5;
          const code = a1[o + 2];
          if (code === 5) continue;
          groups[code].push(i);
        }
        const pos = (i, out) => {
          const o = i * 5;
          out[0] = (a0[o] + (a1[o] - a0[o]) * t) / 10;
          out[1] = (a0[o + 1] + (a1[o + 1] - a0[o + 1]) * t) / 10;
        };
        const p = [0, 0];
        const radius = 1.9;

        // healthy-ish first so infected dots stay on top
        for (const code of [0, 1, 4, 2, 3]) {
          const list = groups[code];
          if (!list.length) continue;
          ctx.fillStyle = CODE_COLORS[code];
          for (const dim of [true, false]) {
            ctx.globalAlpha = dim ? 0.28 : (code === 0 ? 0.8 : 0.95);
            ctx.beginPath();
            for (const i of list) {
              const sheltering = (a1[i * 5 + 4] & 1) !== 0;
              if (sheltering !== dim) continue;
              pos(i, p);
              ctx.moveTo(p[0] + radius, p[1]);
              ctx.arc(p[0], p[1], radius, 0, Math.PI * 2);
            }
            ctx.fill();
          }
        }
        ctx.globalAlpha = 1;

        // glow + pulse for infectious agents
        ctx.globalCompositeOperation = 'lighter';
        for (const i of groups[3]) {
          pos(i, p);
          const flags = a1[i * 5 + 4];
          const pulse = 0.5 + 0.5 * Math.sin(now / 260 + i);
          const glow = ctx.createRadialGradient(p[0], p[1], 0, p[0], p[1], 9);
          glow.addColorStop(0, `rgba(255,77,109,${0.28 + 0.14 * pulse})`);
          glow.addColorStop(1, 'rgba(255,77,109,0)');
          ctx.fillStyle = glow;
          ctx.beginPath(); ctx.arc(p[0], p[1], 9, 0, Math.PI * 2); ctx.fill();
          if ((flags & 2) === 0 && (flags & 1) === 0) {
            ctx.strokeStyle = `rgba(255,77,109,${0.22 * (1 - pulse)})`;
            ctx.lineWidth = 0.8;
            ctx.beginPath(); ctx.arc(p[0], p[1], 4 + 7 * pulse, 0, Math.PI * 2); ctx.stroke();
          }
        }
        ctx.globalCompositeOperation = 'source-over';

        // isolated patients get a thin ring
        ctx.strokeStyle = 'rgba(255,255,255,0.7)';
        ctx.lineWidth = 0.9;
        ctx.beginPath();
        for (const i of groups[3]) {
          if ((a1[i * 5 + 4] & 2) === 0) continue;
          pos(i, p);
          ctx.moveTo(p[0] + 4.2, p[1]);
          ctx.arc(p[0], p[1], 4.2, 0, Math.PI * 2);
        }
        ctx.stroke();
      }

      // labels: below the settlement, or above when there is no room at the bottom
      ctx.textAlign = 'center';
      world.settlements.forEach((st, i) => {
        const infected = frame ? frame.settlements[i] : 0;
        const below = st.y + st.r + 38 < world.height;
        const y1 = below ? st.y + st.r + 16 : st.y - st.r - 22;
        ctx.font = '600 13px Inter, system-ui, sans-serif';
        ctx.fillStyle = 'rgba(230,235,247,0.95)';
        ctx.fillText(st.name, st.x, y1);
        ctx.font = '11px Inter, system-ui, sans-serif';
        ctx.fillStyle = infected > 0 ? 'rgba(255,128,148,0.95)' : 'rgba(136,147,176,0.9)';
        ctx.fillText(`${st.pop.toLocaleString()} · ${infected} ●`, st.x, y1 + 14);
      });
    };
    raf = requestAnimationFrame(draw);
    return () => { cancelAnimationFrame(raf); ro.disconnect(); };
  }, [simRef]);

  const handleClick = (e) => {
    const world = simRef.current.world;
    if (!world) return;
    const rect = canvasRef.current.getBoundingClientRect();
    onSeed(((e.clientX - rect.left) / rect.width) * world.width,
           ((e.clientY - rect.top) / rect.height) * world.height);
  };

  return (
    <div className="map-wrap" ref={wrapRef}>
      <canvas ref={canvasRef} onClick={handleClick} aria-label="Epidemic map" />
      <div className="map-hint">{hint}</div>
    </div>
  );
}
