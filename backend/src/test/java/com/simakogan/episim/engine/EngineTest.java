package com.simakogan.episim.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

    private static Engine run(Params p, long seed, int days) {
        Engine e = new Engine(2000, 5, seed);
        e.setParams(p);
        for (int i = 0; i < days * Engine.TICKS_PER_DAY; i++) e.step();
        return e;
    }

    @Test
    void populationIsConserved() {
        Engine e = run(Params.defaults(), 7, 60);
        int total = e.susceptible() + e.vaccinatedSusceptible() + e.exposed()
                + e.infectious() + e.recovered() + e.dead();
        assertEquals(e.population(), total);
    }

    @Test
    void sameSeedGivesSameOutcome() {
        Engine a = run(Params.defaults(), 42, 40);
        Engine b = run(Params.defaults(), 42, 40);
        assertEquals(a.infectious(), b.infectious());
        assertEquals(a.dead(), b.dead());
        assertEquals(a.everInfected(), b.everInfected());
    }

    @Test
    void strictLockdownReducesInfections() {
        Engine open = run(new Params(0, 0, 0, 0, 0, 2), 3, 80);
        Engine locked = run(new Params(0, 0, 0.8, 0, 0, 2), 3, 80);
        assertTrue(locked.everInfected() < open.everInfected(),
                "lockdown " + locked.everInfected() + " vs open " + open.everInfected());
    }

    @Test
    void vaccinationReducesDeaths() {
        Engine none = run(new Params(0, 0, 0, 0, 0, 2), 5, 120);
        Engine vax = run(new Params(2, 0, 0, 0, 0, 2), 5, 120);
        assertTrue(vax.dead() <= none.dead());
        assertTrue(vax.vaccinatedTotal() > 0);
    }

    @Test
    void noMutationRateMeansSingleVariant() {
        Engine e = run(new Params(0, 0, 0, 0, 0, 2), 9, 80);
        assertEquals(1, e.variantCount());
    }

    @Test
    void higherInfectiousnessSpreadsFaster() {
        Params slow = new Params(0, 0, 0, 0, 0, 2, 0.5, 11, 6, 1.2, 0.85);
        Params fast = new Params(0, 0, 0, 0, 0, 2, 2.0, 11, 6, 1.2, 0.85);
        assertTrue(run(fast, 11, 25).everInfected() > run(slow, 11, 25).everInfected());
    }

    @Test
    void zeroFatalityMeansNoDeaths() {
        Params p = new Params(0, 0, 0, 0, 0, 2, 1.0, 11, 6, 0.0, 0.85);
        assertEquals(0, run(p, 13, 80).dead());
    }

    @Test
    void diseaseParamsAreClamped() {
        Params p = new Params(0, 0, 0, 0, 0, 2, 50, 100, 1, 99, 5).normalized();
        assertEquals(3.0, p.infectiousness());
        assertEquals(20.0, p.contactRadius());
        assertEquals(3.0, p.infectiousDays());
        assertEquals(10.0, p.fatality());
        assertEquals(1.0, p.vaccineEfficacy());
    }

    @Test
    void paramsAreClamped() {
        Params p = new Params(99, -1, 5, Double.NaN, 2, 100).normalized();
        assertEquals(3.0, p.vaccinationRate());
        assertEquals(0.0, p.mutationRate());
        assertEquals(1.0, p.lockdown());
        assertEquals(10.0, p.speed());
    }
}
