package me.arcanebrewery.arcaneBrewery.application.alchemy;
import java.util.*;
/** Raw, obtainable Phase 1 inputs. Prepared/future items are not silently advertised as playable. */
public record ReagentDefinition(String externalId,AlchemyCatalyst catalyst,String materialName,ReagentRole role,
        List<ProfileEffect> effects,int priority,int baseSeconds,boolean risky,double durationMultiplier,double powerMultiplier,double stabilization) {
    public ReagentDefinition(String externalId,AlchemyCatalyst catalyst,String materialName,ReagentRole role,List<ProfileEffect> effects,int priority,int baseSeconds,boolean risky) {this(externalId,catalyst,materialName,role,effects,priority,baseSeconds,risky,1,1,0);}
    public ReagentDefinition {
        if(!Double.isFinite(durationMultiplier)||!Double.isFinite(powerMultiplier)||!Double.isFinite(stabilization)||durationMultiplier<=0||durationMultiplier>2||powerMultiplier<=0||powerMultiplier>2||Math.abs(stabilization)>30)throw new IllegalArgumentException("invalid reagent properties"); effects=List.copyOf(effects);Objects.requireNonNull(role);if(baseSeconds<0 || baseSeconds>120) throw new IllegalArgumentException("invalid duration"); }
    public String id() {return catalyst.id();}
    public String additiveId() {return catalyst.additiveId();}
}
