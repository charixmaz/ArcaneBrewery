package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.*;
import java.util.*;

/** Candidate V3 policy. Not activated for production until version capture is integrated. */
public final class CombinationQuality {
 public static final int VERSION=3;
 public record Targets(int temperature,long cultivation,int extraction,long maturation) { }
 private static double clamp(double v,double low,double high){return Math.max(low,Math.min(high,v));}
 private static double curve(double measured,double target,double width){return 100*Math.exp(-Math.pow((measured-target)/width,2));}
 public Targets targets(ProcessProfile p,Map<String,IngredientLot> lots){
  if(!lots.keySet().equals(p.ingredientRatios().keySet()))throw new IllegalArgumentException("Ingredient snapshot mismatch");
  double sugar=0,starch=0,acid=0,aroma=0,magic=0;
  // Sorted iteration prevents slot/map iteration order from changing floating point accumulation.
  for(var e:new TreeMap<>(p.ingredientRatios()).entrySet()){
   var c=lots.get(e.getKey()).composition();double w=e.getValue();
   sugar+=w*c.sugar();starch+=w*c.starch();acid+=w*c.acidity();aroma+=w*c.aroma();magic+=w*c.magicalAffinity();
  }
  var culture=BrewingCulture.of(p.fermentation().yeastOrCatalyst());
  double interaction=sugar*starch/100; // Ratios affect the response, not just an ingredient count bonus.
  int temp=(int)(2*Math.round(clamp(culture.temperature()+(acid-starch)*.06+magic*.02,10,36)/2));
  long duration=30*Math.round(clamp(culture.seconds()+starch*.9-sugar*.3+interaction*.8,30,300)/30);
  int extraction=(int)(2*Math.round(clamp(72+starch*.12+aroma*.04-acid*.03,60,90)/2));
  double cultivationStress=Math.abs(p.fermentation().temperatureCelsius()-temp)*3
    +Math.abs(p.fermentation().durationSeconds()-duration)*.15;
  double cutsStress=Math.abs(p.distillation().targetTemperatureCelsius()-extraction)*2;
  // Capturable before maturation. The player's actual upstream process changes its response.
  long maturity=15*Math.round(clamp(135+starch*1.1+aroma*.4+interaction+magic*.3+cultivationStress+cutsStress,120,600)/15);
  return new Targets(temp,duration,extraction,maturity);
 }
 public QualityAssessment assess(ProcessProfile p,BatchConditions c,long capturedMaturation){
  if(capturedMaturation<1)throw new IllegalArgumentException("Invalid captured maturation");
  var t=targets(p,c.ingredients());var f=p.fermentation();var d=p.distillation();var a=p.aging();
  double purity=new TreeMap<>(p.ingredientRatios()).entrySet().stream().mapToDouble(e->e.getValue()*c.ingredients().get(e.getKey()).purity()).sum();
  double temp=curve(f.temperatureCelsius(),t.temperature(),8);
  double cultivation=curve(f.durationSeconds(),t.cultivation(),Math.max(45,t.cultivation()*.7));
  double extraction=curve(d.targetTemperatureCelsius(),t.extraction(),10);
  double cuts=clamp(100-Math.abs(d.headsCutPercent()-10)*2-Math.abs(d.tailsCutPercent()-10)*2,0,100);
  double storage=curve(a.storageTemperatureCelsius(),18,10);
  long late=Math.max(0,a.durationSeconds()-capturedMaturation-30);
  double maturation=curve(late,0,Math.max(60,capturedMaturation*.6));
  double stability=p.alchemicalAdditives().isEmpty()?100:c.catalystStability();
  var factors=List.of(new QualityAssessment.Factor("Ingredient purity",purity,.20,"actual ingredient condition"),
   new QualityAssessment.Factor("Mixture temperature response",temp,.20,"compare recorded experiments"),
   new QualityAssessment.Factor("Mixture cultivation response",cultivation,.20,"compare recorded experiments"),
   new QualityAssessment.Factor("Extraction response",extraction,.15,"mixture and extraction interaction"),
   new QualityAssessment.Factor("Cuts",cuts,.05,"captured extraction cuts"),
   new QualityAssessment.Factor("Storage",storage,.05,"captured storage conditions"),
   new QualityAssessment.Factor("Maturation",maturation,.10,"compare recorded harvests"),
   new QualityAssessment.Factor("Stability",stability,.05,"captured equipment and catalysts"));
  double toxicity=new TreeMap<>(p.ingredientRatios()).entrySet().stream().mapToDouble(e->e.getValue()*c.ingredients().get(e.getKey()).composition().toxicity()).sum();
  double penalty=toxicity*.5;
  double score=a.durationSeconds()<capturedMaturation?0:clamp((factors.stream().mapToDouble(x->x.score()*x.weight()).sum()-penalty)*maturation/100,0,100);
  return new QualityAssessment(factors,penalty,score);
 }
}
