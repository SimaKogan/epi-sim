package com.simakogan.episim.engine;

/**
 * Live-adjustable simulation parameters (the sliders in the UI).
 *
 * <p>Control sliders: vaccination, mutation and restrictions. Disease sliders describe the virus itself.</p>
 *
 * @param vaccinationRate  share of the whole population vaccinated per day, in percent (0..3)
 * @param mutationRate     relative mutation pressure (0..1); scales the chance that an infection spawns a new variant
 * @param lockdown         share of people sheltering at home (0..1)
 * @param masks            mask compliance (0..1); lowers per-contact transmission probability
 * @param testing          testing &amp; isolation intensity (0..1); chance that infectious people get isolated
 * @param speed            simulated days per real second (0.5..10)
 * @param infectiousness   transmission multiplier relative to the baseline virus (0.3..3)
 * @param contactRadius    distance that counts as a close contact (4..20 units)
 * @param infectiousDays   mean length of the infectious period (3..14 days)
 * @param fatality         baseline infection fatality rate in percent (0..10)
 * @param vaccineEfficacy  protection of a vaccinated person against the original variant (0..1)
 */
public record Params(double vaccinationRate, double mutationRate, double lockdown,
                     double masks, double testing, double speed,
                     double infectiousness, double contactRadius, double infectiousDays,
                     double fatality, double vaccineEfficacy) {

    /** Convenience constructor: control sliders only, disease at its defaults. */
    public Params(double vaccinationRate, double mutationRate, double lockdown,
                  double masks, double testing, double speed) {
        this(vaccinationRate, mutationRate, lockdown, masks, testing, speed, 1.0, 11.0, 6.0, 1.2, 0.85);
    }

    public static Params defaults() {
        return new Params(0.0, 0.5, 0.0, 0.0, 0.0, 2.0);
    }

    /** Returns a copy with every field clamped into its valid range. */
    public Params normalized() {
        return new Params(
                clamp(vaccinationRate, 0, 3),
                clamp(mutationRate, 0, 1),
                clamp(lockdown, 0, 1),
                clamp(masks, 0, 1),
                clamp(testing, 0, 1),
                clamp(speed, 0.5, 10),
                clamp(infectiousness, 0.3, 3),
                clamp(contactRadius, 4, 20),
                clamp(infectiousDays, 3, 14),
                clamp(fatality, 0, 10),
                clamp(vaccineEfficacy, 0, 1));
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
