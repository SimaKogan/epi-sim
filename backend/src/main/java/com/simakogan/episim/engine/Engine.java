package com.simakogan.episim.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Agent-based SEIR(+V) epidemic engine.
 *
 * <p>Every person is an agent that roams inside a settlement, occasionally travels to another one,
 * and moves through the states S → E → I → R / D. Transmission happens between nearby agents
 * (spatial hash grid), is scaled by variant infectiousness, immunity (vaccine / past infection /
 * immune escape of the variant), masks and lockdown. New variants can emerge on infection.</p>
 *
 * <p>The engine is NOT thread-safe: drive it from a single thread.</p>
 */
public final class Engine {

    public static final int WIDTH = 1000;
    public static final int HEIGHT = 600;
    public static final int TICKS_PER_DAY = 8;
    public static final int MAX_VARIANTS = 8;

    private static final byte S = 0, E = 1, I = 2, R = 3, D = 4;

    private static final double GRID_CELL = 20.0;          // spatial grid cell = maximum contact radius
    private static final double BASE_P = 0.0105;          // per contact per tick, before modifiers
    private static final double DENSITY = 0.03;           // agents per unit^2 inside settlements
    private static final double ROAM_SPEED = 1.5;
    private static final double TRAVEL_SPEED = 7.0;
    private static final double TRAVEL_P = 0.0012;        // per tick, per agent at home
    private static final double MUTATION_SCALE = 0.0004;  // chance per infection at mutationRate = 1
    private static final double MEAN_INFECTIOUS_DAYS = 6.0;

    private static final String[] SETTLEMENT_NAMES =
            {"Aurora", "Basalt", "Cobalt", "Delta", "Ember", "Fjord", "Garnet"};
    private static final double[] SETTLEMENT_WEIGHTS = {0.27, 0.20, 0.16, 0.13, 0.10, 0.08, 0.06};
    private static final String[] GREEK =
            {"Origin", "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta"};

    public record Settlement(int id, String name, float x, float y, float r, int pop) { }

    public static final class Variant {
        public final int id;
        public final String name;
        public final int parent;
        public final double beta;            // relative infectiousness
        public final double escape;          // 0..1 immune escape
        public final double ifr;             // relative lethality
        public final double incubationDays;
        public final double infectiousDays;
        public final double bornDay;
        int active;
        int total;

        Variant(int id, int parent, double beta, double escape, double ifr,
                double incubationDays, double infectiousDays, double bornDay) {
            this.id = id;
            this.name = GREEK[id];
            this.parent = parent;
            this.beta = beta;
            this.escape = escape;
            this.ifr = ifr;
            this.incubationDays = incubationDays;
            this.infectiousDays = infectiousDays;
            this.bornDay = bornDay;
        }

        public int active() { return active; }
        public int total() { return total; }
    }

    // ---- world ----
    private final SplittableRandom rnd;
    private final long seed;
    private final int n;
    private final List<Settlement> settlements = new ArrayList<>();
    private final List<int[]> links = new ArrayList<>();
    private final List<Variant> variants = new ArrayList<>();

    // ---- agents (structure of arrays) ----
    private final float[] x, y, hx, hy, tx, ty, compliance, risk, recoverDay;
    private final short[] home, dest;
    private final byte[] state, variant, mode;
    private final boolean[] vaccinated, isolated;
    private final int[] timer, immuneMask, stay;

    // ---- spatial grid of infectious agents ----
    private final int cols, rows;
    private final int[] head;
    private final int[] nextInfectious;

    // ---- state ----
    private Params params = Params.defaults();
    private long tick = 0;
    private double vaxAccumulator = 0;
    private int vaccinatedTotal = 0;
    private int deaths = 0;
    private long everInfected = 0;
    private int peakInfected = 0;
    private double peakDay = 0;
    private int newThisTick = 0;
    private final int[] newRing = new int[TICKS_PER_DAY * 3];
    private int ringPos = 0;
    private double rt = Double.NaN;
    private boolean extinctLogged = false;
    private boolean firstDeathLogged = false;
    private int infectedMilestone = 0;

    // counts refreshed each tick
    private int cS, cV, cE, cI, cR, cD;
    private final int[] settlementInfected;

    private final List<LogEntry> pendingLog = new ArrayList<>();
    private final List<LogEntry> recentLog = new ArrayList<>();
    private final List<double[]> history = new ArrayList<>();

    public Engine(int population, int initialInfected, long seed) {
        this.n = Math.max(200, Math.min(6000, population));
        this.seed = seed;
        this.rnd = new SplittableRandom(seed);

        x = new float[n]; y = new float[n]; hx = new float[n]; hy = new float[n];
        tx = new float[n]; ty = new float[n]; compliance = new float[n]; risk = new float[n];
        recoverDay = new float[n];
        home = new short[n]; dest = new short[n];
        state = new byte[n]; variant = new byte[n]; mode = new byte[n];
        vaccinated = new boolean[n]; isolated = new boolean[n];
        timer = new int[n]; immuneMask = new int[n]; stay = new int[n];

        cols = (int) Math.ceil(WIDTH / GRID_CELL) + 1;
        rows = (int) Math.ceil(HEIGHT / GRID_CELL) + 1;
        head = new int[cols * rows];
        nextInfectious = new int[n];

        buildWorld();
        populate();
        variants.add(new Variant(0, -1, 1.0, 0.0, 1.0, 3.0, MEAN_INFECTIOUS_DAYS, 0));
        settlementInfected = new int[settlements.size()];

        int seedCount = Math.max(1, Math.min(initialInfected, n / 4));
        seedOutbreak(seedCount);
        refreshCounts();
        snapshotDay();
        String startName = settlements.get(homeOfFirstInfected()).name();
        log("start", "Simulation started: " + n + " people, " + seedCount + " initial cases in "
                + startName + ".", String.valueOf(n), String.valueOf(seedCount), startName);
    }

    // =====================================================================
    // setup
    // =====================================================================

    private void buildWorld() {
        int count = SETTLEMENT_NAMES.length;
        int[] pops = new int[count];
        int assigned = 0;
        for (int k = 0; k < count; k++) {
            pops[k] = (int) Math.round(n * SETTLEMENT_WEIGHTS[k]);
            assigned += pops[k];
        }
        pops[0] += n - assigned;

        float[] sx = new float[count], sy = new float[count], sr = new float[count];
        for (int k = 0; k < count; k++) {
            sr[k] = (float) Math.sqrt(pops[k] / (Math.PI * DENSITY));
        }
        for (int k = 0; k < count; k++) {
            boolean placed = false;
            for (int attempt = 0; attempt < 4000 && !placed; attempt++) {
                float margin = sr[k] + 14;
                float px = (float) (margin + rnd.nextDouble() * (WIDTH - 2 * margin));
                float py = (float) (margin + rnd.nextDouble() * (HEIGHT - 2 * margin));
                double gap = attempt < 3000 ? 36 : 12;
                boolean ok = true;
                for (int j = 0; j < k && ok; j++) {
                    if (Math.hypot(px - sx[j], py - sy[j]) < sr[k] + sr[j] + gap) ok = false;
                }
                if (ok) { sx[k] = px; sy[k] = py; placed = true; }
            }
            if (!placed) { // extremely unlikely fallback: deterministic grid slot
                sx[k] = 120 + (k % 4) * 250;
                sy[k] = 150 + (k / 4) * 300;
            }
        }
        for (int k = 0; k < count; k++) {
            settlements.add(new Settlement(k, SETTLEMENT_NAMES[k], sx[k], sy[k], sr[k], pops[k]));
        }
        // routes: every settlement linked to its two nearest neighbours
        boolean[][] linked = new boolean[count][count];
        for (int a = 0; a < count; a++) {
            Integer[] order = new Integer[count - 1];
            int idx = 0;
            for (int b = 0; b < count; b++) if (b != a) order[idx++] = b;
            final int fa = a;
            Arrays.sort(order, (p, q) -> Double.compare(
                    Math.hypot(sx[fa] - sx[p], sy[fa] - sy[p]),
                    Math.hypot(sx[fa] - sx[q], sy[fa] - sy[q])));
            for (int m = 0; m < 2 && m < order.length; m++) {
                int b = order[m];
                if (!linked[a][b]) {
                    linked[a][b] = linked[b][a] = true;
                    links.add(new int[]{Math.min(a, b), Math.max(a, b)});
                }
            }
        }
    }

    private void populate() {
        int i = 0;
        for (Settlement s : settlements) {
            for (int k = 0; k < s.pop() && i < n; k++, i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double rad = s.r() * 0.94 * Math.sqrt(rnd.nextDouble());
                hx[i] = x[i] = (float) (s.x() + Math.cos(ang) * rad);
                hy[i] = y[i] = (float) (s.y() + Math.sin(ang) * rad);
                home[i] = (short) s.id();
                dest[i] = -1;
                compliance[i] = (float) rnd.nextDouble();
                double u = rnd.nextDouble();
                risk[i] = (float) (u < 0.55 ? 0.3 : u < 0.85 ? 1.2 : 4.5);
                state[i] = S;
                newRoamTarget(i, s, true);
            }
        }
    }

    private int homeOfFirstInfected() {
        for (int i = 0; i < n; i++) if (state[i] == E) return home[i];
        return 0;
    }

    private void seedOutbreak(int count) {
        Settlement s = settlements.get(rnd.nextInt(settlements.size()));
        int done = 0, guard = 0;
        while (done < count && guard++ < n * 20) {
            int i = rnd.nextInt(n);
            if (home[i] == s.id() && state[i] == S) {
                startInfection(i, 0);
                done++;
            }
        }
    }

    // =====================================================================
    // external control (call from the simulation thread only)
    // =====================================================================

    public void setParams(Params p) { this.params = p.normalized(); }

    public Params params() { return params; }

    /** Infects up to {@code count} susceptible/recovered agents close to the world point (px, py). */
    public int infectNear(double px, double py, int count) {
        Integer[] idx = new Integer[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if ((state[i] == S || state[i] == R) && Math.hypot(x[i] - px, y[i] - py) < 45) idx[m++] = i;
        }
        Arrays.sort(idx, 0, m, (a, b) -> Double.compare(
                Math.hypot(x[a] - px, y[a] - py), Math.hypot(x[b] - px, y[b] - py)));
        int best = 0;
        for (Variant v : variants) if (v.active > variants.get(best).active) best = v.id;
        int done = 0;
        for (int k = 0; k < m && done < count; k++) {
            startInfection(idx[k], best);
            done++;
        }
        if (done > 0) {
            log("manual", "Manual outbreak: " + done + " new " + variants.get(best).name + " cases.",
                    String.valueOf(done), variants.get(best).name);
        }
        return done;
    }

    // =====================================================================
    // simulation step
    // =====================================================================

    public void step() {
        tick++;
        final double day = day();
        final Params p = params;
        newThisTick = 0;

        move(p);
        transmit(p, day);
        progress(p, day);
        vaccinate(p);

        newRing[ringPos] = newThisTick;
        ringPos = (ringPos + 1) % newRing.length;

        refreshCounts();
        updateRt();

        if (cI > peakInfected) { peakInfected = cI; peakDay = day; }
        if (tick % TICKS_PER_DAY == 0) snapshotDay();
        milestones();
    }

    private void move(Params p) {
        final double lock = p.lockdown();
        for (int i = 0; i < n; i++) {
            if (state[i] == D) continue;
            final boolean sheltering = compliance[i] < lock || isolated[i];
            if (sheltering) {
                if (mode[i] != 3) { mode[i] = 3; tx[i] = hx[i]; ty[i] = hy[i]; dest[i] = -1; }
                float dx = hx[i] - x[i], dy = hy[i] - y[i];
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 0.01) {
                    double sp = mode[i] == 3 && d > 60 ? TRAVEL_SPEED : ROAM_SPEED * 1.4;
                    if (d <= sp) { x[i] = hx[i]; y[i] = hy[i]; }
                    else { x[i] += dx / d * sp; y[i] += dy / d * sp; }
                }
                continue;
            }
            if (mode[i] == 2 && --stay[i] <= 0) {
                mode[i] = 3; tx[i] = hx[i]; ty[i] = hy[i];
            }
            float dx = tx[i] - x[i], dy = ty[i] - y[i];
            double d = Math.sqrt(dx * dx + dy * dy);
            double sp = (mode[i] == 1 || (mode[i] == 3 && d > 60)) ? TRAVEL_SPEED : ROAM_SPEED;
            if (d <= sp) {
                x[i] = tx[i]; y[i] = ty[i];
                arrive(i);
            } else {
                x[i] += dx / d * sp + (float) ((rnd.nextDouble() - 0.5) * 0.5);
                y[i] += dy / d * sp + (float) ((rnd.nextDouble() - 0.5) * 0.5);
            }
            if (mode[i] == 0 && rnd.nextDouble() < TRAVEL_P) startTrip(i);
        }
    }

    private void arrive(int i) {
        switch (mode[i]) {
            case 0 -> newRoamTarget(i, settlements.get(home[i]), true);
            case 1 -> {
                mode[i] = 2;
                stay[i] = 10 + rnd.nextInt(30);
                newRoamTarget(i, settlements.get(dest[i]), false);
            }
            case 2 -> newRoamTarget(i, settlements.get(dest[i]), false);
            default -> { // 3: back home
                mode[i] = 0;
                dest[i] = -1;
                newRoamTarget(i, settlements.get(home[i]), true);
            }
        }
    }

    private void newRoamTarget(int i, Settlement s, boolean fromHome) {
        double cx, cy, rad;
        if (fromHome && rnd.nextDouble() < 0.75) { cx = hx[i]; cy = hy[i]; rad = 40; }
        else { cx = s.x(); cy = s.y(); rad = s.r() * 0.94; }
        double ang = rnd.nextDouble() * Math.PI * 2;
        double r = rad * Math.sqrt(rnd.nextDouble());
        tx[i] = (float) clamp(cx + Math.cos(ang) * r, 2, WIDTH - 2);
        ty[i] = (float) clamp(cy + Math.sin(ang) * r, 2, HEIGHT - 2);
    }

    private void startTrip(int i) {
        Settlement from = settlements.get(home[i]);
        double total = 0;
        double[] w = new double[settlements.size()];
        for (Settlement s : settlements) {
            if (s.id() == from.id()) continue;
            double dist = Math.hypot(s.x() - from.x(), s.y() - from.y());
            w[s.id()] = s.pop() / Math.max(80.0, dist);
            total += w[s.id()];
        }
        double pick = rnd.nextDouble() * total;
        int target = from.id() == 0 ? 1 : 0;
        for (int k = 0; k < w.length; k++) {
            if (w[k] <= 0) continue;
            pick -= w[k];
            target = k;
            if (pick <= 0) break;
        }
        Settlement t = settlements.get(target);
        dest[i] = (short) target;
        mode[i] = 1;
        double ang = rnd.nextDouble() * Math.PI * 2;
        double r = t.r() * 0.9 * Math.sqrt(rnd.nextDouble());
        tx[i] = (float) (t.x() + Math.cos(ang) * r);
        ty[i] = (float) (t.y() + Math.sin(ang) * r);
    }

    private boolean sheltering(int i, double lock) {
        return compliance[i] < lock || isolated[i];
    }

    private void transmit(Params p, double day) {
        // index infectious, non-sheltering agents
        Arrays.fill(head, -1);
        int infectious = 0;
        final double lock = p.lockdown();
        for (int i = 0; i < n; i++) {
            if (state[i] == I && !sheltering(i, lock)) {
                int c = cell(x[i], y[i]);
                nextInfectious[i] = head[c];
                head[c] = i;
                infectious++;
            }
        }
        if (infectious == 0) return;

        final double maskFactor = 1.0 - 0.7 * p.masks();
        final double rad = Math.min(GRID_CELL, p.contactRadius());
        final double r2 = rad * rad;
        for (int i = 0; i < n; i++) {
            byte st = state[i];
            if ((st != S && st != R) || sheltering(i, lock)) continue;
            int cx = (int) (x[i] / GRID_CELL), cy = (int) (y[i] / GRID_CELL);
            boolean infected = false;
            for (int gx = Math.max(0, cx - 1); gx <= Math.min(cols - 1, cx + 1) && !infected; gx++) {
                for (int gy = Math.max(0, cy - 1); gy <= Math.min(rows - 1, cy + 1) && !infected; gy++) {
                    for (int j = head[gy * cols + gx]; j >= 0; j = nextInfectious[j]) {
                        float dx = x[j] - x[i], dy = y[j] - y[i];
                        if (dx * dx + dy * dy > r2) continue;
                        Variant v = variants.get(variant[j]);
                        double prob = BASE_P * p.infectiousness() * v.beta * susceptibility(i, v, day, p.vaccineEfficacy()) * maskFactor;
                        if (rnd.nextDouble() < prob) {
                            infect(i, v, p);
                            infected = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    private double susceptibility(int i, Variant v, double day, double efficacy) {
        double f = 1.0;
        if (vaccinated[i]) f = 1.0 - efficacy * (1.0 - v.escape);
        if (state[i] == R) {
            boolean same = ((immuneMask[i] >> v.id) & 1) != 0;
            double base = same ? 0.03 : 0.05 + 0.9 * v.escape;
            double wane = Math.min(1.0, (day - recoverDay[i]) / 220.0);
            base = base + (1.0 - base) * 0.5 * wane;
            f = Math.min(f, base);
        }
        return f;
    }

    private void infect(int i, Variant source, Params p) {
        Variant v = source;
        if (variants.size() < MAX_VARIANTS && rnd.nextDouble() < p.mutationRate() * MUTATION_SCALE) {
            v = spawnVariant(source, i);
        }
        startInfection(i, v.id);
        newThisTick++;
    }

    private void startInfection(int i, int variantId) {
        Variant v = variants.get(variantId);
        state[i] = E;
        variant[i] = (byte) variantId;
        timer[i] = ticks(v.incubationDays);
        v.active++;
        v.total++;
        everInfected++;
    }

    private Variant spawnVariant(Variant parent, int carrier) {
        int id = variants.size();
        double beta = clamp(parent.beta * Math.exp(0.05 + 0.22 * gauss()), 0.6, 2.4);
        double escape = clamp(parent.escape + 0.05 + 0.25 * rnd.nextDouble(), 0.0, 0.95);
        double ifr = clamp(parent.ifr * Math.exp(-0.05 + 0.25 * gauss()), 0.3, 2.5);
        double incubation = clamp(parent.incubationDays * Math.exp(0.1 * gauss()), 1.5, 5.0);
        double infectious = clamp(parent.infectiousDays * Math.exp(0.1 * gauss()), 3.5, 9.0);
        Variant v = new Variant(id, parent.id, beta, escape, ifr, incubation, infectious, day());
        variants.add(v);
        String where = settlements.get(home[carrier]).name();
        log("variant", "New variant " + v.name + " emerged in " + where
                + " (infectiousness x" + fmt(beta) + ", immune escape " + Math.round(escape * 100)
                + "%, lethality x" + fmt(ifr) + ").",
                v.name, where, fmt(beta), String.valueOf(Math.round(escape * 100)), fmt(ifr));
        return v;
    }

    private void progress(Params p, double day) {
        final double testProb = p.testing() * 0.6 / TICKS_PER_DAY;
        for (int i = 0; i < n; i++) {
            byte st = state[i];
            if (st == E) {
                if (--timer[i] <= 0) {
                    state[i] = I;
                    timer[i] = ticks(variants.get(variant[i]).infectiousDays * p.infectiousDays() / MEAN_INFECTIOUS_DAYS);
                }
            } else if (st == I) {
                if (!isolated[i] && testProb > 0 && rnd.nextDouble() < testProb) isolated[i] = true;
                if (--timer[i] <= 0) {
                    Variant v = variants.get(variant[i]);
                    v.active--;
                    double death = Math.min(0.9, p.fatality() / 100.0 * v.ifr * risk[i] * (vaccinated[i] ? 0.2 : 1.0));
                    isolated[i] = false;
                    if (rnd.nextDouble() < death) {
                        state[i] = D;
                        deaths++;
                    } else {
                        state[i] = R;
                        immuneMask[i] |= 1 << v.id;
                        recoverDay[i] = (float) day;
                    }
                }
            }
        }
    }

    private void vaccinate(Params p) {
        if (p.vaccinationRate() <= 0) return;
        vaxAccumulator += p.vaccinationRate() / 100.0 * n / TICKS_PER_DAY;
        while (vaxAccumulator >= 1.0) {
            vaxAccumulator -= 1.0;
            for (int tries = 0; tries < 40; tries++) {
                int j = rnd.nextInt(n);
                if ((state[j] == S || state[j] == R) && !vaccinated[j]) {
                    vaccinated[j] = true;
                    vaccinatedTotal++;
                    break;
                }
            }
        }
    }

    private void refreshCounts() {
        cS = cV = cE = cI = cR = cD = 0;
        Arrays.fill(settlementInfected, 0);
        for (int i = 0; i < n; i++) {
            switch (state[i]) {
                case S -> { if (vaccinated[i]) cV++; else cS++; }
                case E -> cE++;
                case I -> { cI++; settlementInfected[home[i]]++; }
                case R -> cR++;
                default -> cD++;
            }
        }
    }

    private void updateRt() {
        int sum = 0;
        for (int v : newRing) sum += v;
        if (cI < 5) { rt = Double.NaN; return; }
        double perDay = sum / 3.0;
        double est = perDay * params.infectiousDays() / cI;
        rt = Double.isNaN(rt) ? est : rt * 0.9 + est * 0.1;
    }

    private void milestones() {
        if (!firstDeathLogged && deaths > 0) {
            firstDeathLogged = true;
            log("death", "First death recorded.");
        }
        int pct = (int) (everInfected * 100 / n);
        int step = pct / 25;
        if (step > infectedMilestone && step <= 3) {
            infectedMilestone = step;
            log("milestone", step * 25 + "% of the population has been infected at least once.", String.valueOf(step * 25));
        }
        if (!extinctLogged && cE + cI == 0 && tick > TICKS_PER_DAY) {
            extinctLogged = true;
            log("contained", "Outbreak contained: no active cases left.");
        }
        if (extinctLogged && cE + cI > 0) extinctLogged = false;
    }

    private void snapshotDay() {
        history.add(new double[]{day(), cS, cV, cE, cI, cR, cD, Double.isNaN(rt) ? -1 : rt});
    }

    // =====================================================================
    // serialization (hand-written JSON keeps the engine dependency free)
    // =====================================================================

    public String worldJson() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"width\":").append(WIDTH).append(",\"height\":").append(HEIGHT)
          .append(",\"ticksPerDay\":").append(TICKS_PER_DAY)
          .append(",\"population\":").append(n)
          .append(",\"seed\":").append(seed)
          .append(",\"settlements\":[");
        for (int k = 0; k < settlements.size(); k++) {
            Settlement s = settlements.get(k);
            if (k > 0) sb.append(',');
            sb.append("{\"id\":").append(s.id()).append(",\"name\":\"").append(s.name())
              .append("\",\"x\":").append(round1(s.x())).append(",\"y\":").append(round1(s.y()))
              .append(",\"r\":").append(round1(s.r())).append(",\"pop\":").append(s.pop()).append('}');
        }
        sb.append("],\"links\":[");
        for (int k = 0; k < links.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append('[').append(links.get(k)[0]).append(',').append(links.get(k)[1]).append(']');
        }
        sb.append("]}");
        return sb.toString();
    }

    public String historyJson() {
        StringBuilder sb = new StringBuilder(history.size() * 48 + 256);
        sb.append("{\"days\":[");
        for (int k = 0; k < history.size(); k++) {
            double[] h = history.get(k);
            if (k > 0) sb.append(',');
            sb.append('[');
            for (int c = 0; c < h.length; c++) {
                if (c > 0) sb.append(',');
                if (c == 0 || c == 7) sb.append(num(h[c])); else sb.append((int) h[c]);
            }
            sb.append(']');
        }
        sb.append("],\"log\":");
        appendLog(sb, recentLog);
        sb.append('}');
        return sb.toString();
    }

    /** Builds one animation frame. Drains the pending log. */
    public String frameJson(boolean running) {
        StringBuilder sb = new StringBuilder(n * 24 + 2048);
        double day = day();
        String status = cE + cI == 0 ? "contained" : running ? "running" : "paused";
        sb.append("{\"tick\":").append(tick)
          .append(",\"day\":").append(num(day))
          .append(",\"status\":\"").append(status).append('"')
          .append(",\"counts\":[").append(cS).append(',').append(cV).append(',').append(cE).append(',')
          .append(cI).append(',').append(cR).append(',').append(cD).append(']')
          .append(",\"stats\":{\"rt\":").append(Double.isNaN(rt) ? "null" : num(rt))
          .append(",\"peak\":").append(peakInfected)
          .append(",\"peakDay\":").append(num(peakDay))
          .append(",\"deaths\":").append(deaths)
          .append(",\"everInfected\":").append(everInfected)
          .append(",\"vaccinated\":").append(vaccinatedTotal)
          .append(",\"population\":").append(n).append('}');
        Params p = params;
        sb.append(",\"params\":{\"vaccinationRate\":").append(num(p.vaccinationRate()))
          .append(",\"mutationRate\":").append(num(p.mutationRate()))
          .append(",\"lockdown\":").append(num(p.lockdown()))
          .append(",\"masks\":").append(num(p.masks()))
          .append(",\"testing\":").append(num(p.testing()))
          .append(",\"speed\":").append(num(p.speed()))
          .append(",\"infectiousness\":").append(num(p.infectiousness()))
          .append(",\"contactRadius\":").append(num(p.contactRadius()))
          .append(",\"infectiousDays\":").append(num(p.infectiousDays()))
          .append(",\"fatality\":").append(num(p.fatality()))
          .append(",\"vaccineEfficacy\":").append(num(p.vaccineEfficacy())).append('}');
        sb.append(",\"variants\":[");
        for (int k = 0; k < variants.size(); k++) {
            Variant v = variants.get(k);
            if (k > 0) sb.append(',');
            sb.append("{\"id\":").append(v.id).append(",\"name\":\"").append(v.name)
              .append("\",\"parent\":").append(v.parent)
              .append(",\"beta\":").append(num(v.beta))
              .append(",\"escape\":").append(num(v.escape))
              .append(",\"ifr\":").append(num(v.ifr))
              .append(",\"born\":").append(num(v.bornDay))
              .append(",\"active\":").append(v.active)
              .append(",\"total\":").append(v.total).append('}');
        }
        sb.append("],\"settlements\":[");
        for (int k = 0; k < settlementInfected.length; k++) {
            if (k > 0) sb.append(',');
            sb.append(settlementInfected[k]);
        }
        sb.append("],\"log\":");
        appendLog(sb, pendingLog);
        recentLog.addAll(pendingLog);
        while (recentLog.size() > 60) recentLog.remove(0);
        pendingLog.clear();
        // agents: x*10, y*10, code, variant, flags (bit0 sheltering, bit1 isolated)
        sb.append(",\"agents\":[");
        final double lock = p.lockdown();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            int code = switch (state[i]) {
                case S -> vaccinated[i] ? 1 : 0;
                case E -> 2;
                case I -> 3;
                case R -> 4;
                default -> 5;
            };
            int flags = (compliance[i] < lock ? 1 : 0) | (isolated[i] ? 2 : 0);
            sb.append(Math.round(x[i] * 10)).append(',').append(Math.round(y[i] * 10)).append(',')
              .append(code).append(',').append(variant[i]).append(',').append(flags);
        }
        sb.append("]}");
        return sb.toString();
    }

    private void appendLog(StringBuilder sb, List<LogEntry> entries) {
        sb.append('[');
        for (int k = 0; k < entries.size(); k++) {
            LogEntry e = entries.get(k);
            if (k > 0) sb.append(',');
            sb.append("{\"day\":").append(e.day()).append(",\"type\":\"").append(escape(e.type()))
              .append("\",\"args\":[");
            for (int a = 0; a < e.args().length; a++) {
                if (a > 0) sb.append(',');
                sb.append('"').append(escape(e.args()[a])).append('"');
            }
            sb.append("],\"msg\":\"").append(escape(e.msg())).append("\"}");
        }
        sb.append(']');
    }

    // =====================================================================
    // read access (tests / diagnostics)
    // =====================================================================

    public int population() { return n; }
    public long tick() { return tick; }
    public double day() { return tick / (double) TICKS_PER_DAY; }
    public int susceptible() { return cS; }
    public int vaccinatedSusceptible() { return cV; }
    public int exposed() { return cE; }
    public int infectious() { return cI; }
    public int recovered() { return cR; }
    public int dead() { return cD; }
    public int variantCount() { return variants.size(); }
    public int peakInfected() { return peakInfected; }
    public int vaccinatedTotal() { return vaccinatedTotal; }
    public long everInfected() { return everInfected; }
    public List<Settlement> settlements() { return List.copyOf(settlements); }

    // =====================================================================
    // helpers
    // =====================================================================

    private int cell(float px, float py) {
        int cx = Math.min(cols - 1, Math.max(0, (int) (px / GRID_CELL)));
        int cy = Math.min(rows - 1, Math.max(0, (int) (py / GRID_CELL)));
        return cy * cols + cx;
    }

    private int ticks(double days) {
        double jitter = 0.8 + rnd.nextDouble() * 0.4;
        return Math.max(1, (int) Math.round(days * TICKS_PER_DAY * jitter));
    }

    private double gauss() {
        double u1 = Math.max(1e-12, rnd.nextDouble());
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * rnd.nextDouble());
    }

    private record LogEntry(String day, String type, String[] args, String msg) { }

    private void log(String type, String msg, String... args) {
        pendingLog.add(new LogEntry(num(day()), type, args, msg));
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String fmt(double v) { return String.valueOf(Math.round(v * 100) / 100.0); }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        return String.valueOf(Math.round(v * 100) / 100.0);
    }

    private static String round1(double v) { return String.valueOf(Math.round(v * 10) / 10.0); }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> { if (c < 0x20) b.append(' '); else b.append(c); }
            }
        }
        return b.toString();
    }
}
