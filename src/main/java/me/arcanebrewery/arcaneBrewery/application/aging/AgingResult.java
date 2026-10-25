package me.arcanebrewery.arcaneBrewery.application.aging;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import me.arcanebrewery.arcaneBrewery.domain.quality.BatchSimulationResult;
import java.util.Objects;

public record AgingResult(ProcessProfile process, BatchSimulationResult simulation, me.arcanebrewery.arcaneBrewery.domain.quality.QualityAssessment assessment) {
    public AgingResult(ProcessProfile process,BatchSimulationResult simulation) { this(process,simulation,null); }
    public AgingResult {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(simulation, "simulation");
    }
}
