package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

/** Biological burden and toxicity are separate; toxic takes precedence in the display state. */
public record Contamination(double burden, double toxicity) {
    public enum State { CLEAN, MINOR, SIGNIFICANT, DANGEROUS, SPOILED, TOXIC }

    public Contamination {
        burden = DomainValues.percent(burden);
        toxicity = DomainValues.percent(toxicity);
    }

    public State state() {
        if (toxicity >= 50) return State.TOXIC;
        if (burden >= 80) return State.SPOILED;
        if (burden >= 60) return State.DANGEROUS;
        if (burden >= 30) return State.SIGNIFICANT;
        if (burden >= 5) return State.MINOR;
        return State.CLEAN;
    }
}
