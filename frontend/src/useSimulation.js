import { useEffect, useRef, useState } from 'react';

/**
 * Subscribes to the backend SSE stream.
 *
 * High-frequency data (agent positions) lives in a ref that the canvas reads inside its own
 * requestAnimationFrame loop; React state is refreshed at ~8 Hz for the panels and charts.
 */
export function useSimulation() {
  const sim = useRef({
    world: null,
    prev: null, curr: null, prevTime: 0, currTime: 0,
    frame: null,
    history: [], lastDay: -1,
    log: [],
    version: 0,
  });
  const [connected, setConnected] = useState(false);
  const [ui, setUi] = useState({ world: null, frame: null, version: 0, log: [] });

  useEffect(() => {
    const s = sim.current;
    const es = new EventSource('/api/sim/stream');
    es.onopen = () => setConnected(true);
    es.onerror = () => setConnected(false);

    es.addEventListener('world', (e) => {
      s.world = JSON.parse(e.data);
      s.prev = s.curr = null;
      s.history = []; s.lastDay = -1; s.log = [];
      s.version++;
    });

    es.addEventListener('history', (e) => {
      const h = JSON.parse(e.data);
      s.history = h.days;
      s.lastDay = h.days.length ? Math.floor(h.days[h.days.length - 1][0]) : -1;
      s.log = h.log || [];
      s.version++;
    });

    es.addEventListener('frame', (e) => {
      const f = JSON.parse(e.data);
      const now = performance.now();
      const agents = Int32Array.from(f.agents);
      s.prev = s.curr || agents;
      s.prevTime = s.curr ? s.currTime : now - 33;
      s.curr = agents;
      s.currTime = now;
      s.frame = f;

      const day = Math.floor(f.day);
      if (day > s.lastDay) {
        const c = f.counts;
        s.history.push([f.day, c[0], c[1], c[2], c[3], c[4], c[5], f.stats.rt == null ? -1 : f.stats.rt]);
        s.lastDay = day;
        s.version++;
      }
      if (f.log && f.log.length) {
        s.log = s.log.concat(f.log).slice(-60);
      }
    });

    const timer = setInterval(() => {
      setUi({ world: s.world, frame: s.frame, version: s.version, log: s.log });
    }, 120);

    return () => { clearInterval(timer); es.close(); };
  }, []);

  return { simRef: sim, ui, connected };
}
