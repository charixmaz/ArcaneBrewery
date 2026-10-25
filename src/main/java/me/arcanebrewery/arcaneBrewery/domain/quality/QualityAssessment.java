package me.arcanebrewery.arcaneBrewery.domain.quality;

import java.util.List;

/** Workmanship is independent of potency; losses are attributable to measured conditions. */
public record QualityAssessment(List<Factor> factors,double contaminationPenalty,double score) {
    public record Factor(String name,double score,double weight,String explanation) { }
    public QualityAssessment { factors=List.copyOf(factors); }
    public List<String> breakdown() {
        var lines=new java.util.ArrayList<String>();
        for(var f:factors) lines.add(f.name()+": "+Math.round(f.score())+"/100 — "+f.explanation());
        lines.add("Contamination penalty: -"+Math.round(contaminationPenalty));
        lines.add("Quality: "+Math.round(score)+"%");
        return List.copyOf(lines);
    }
}
