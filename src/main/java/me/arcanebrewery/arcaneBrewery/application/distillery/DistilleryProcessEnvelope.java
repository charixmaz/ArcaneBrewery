package me.arcanebrewery.arcaneBrewery.application.distillery;
import java.util.Objects;
/** Captures the effects policy at extraction start without changing legacy process/fingerprint encoding. */
public record DistilleryProcessEnvelope(DistilleryBatchState state,int effectGeneration) {
    public DistilleryProcessEnvelope {Objects.requireNonNull(state);if(effectGeneration<0||effectGeneration>2)throw new IllegalArgumentException("unsupported effects generation");}
    public String encode(){return(effectGeneration>0?"effects-v"+effectGeneration+":":"")+new DistilleryStateCodec().encodeBase64(state);}
    public static DistilleryProcessEnvelope decode(String value){
        int generation=value.startsWith("effects-v2:")?2:value.startsWith("effects-v1:")?1:0;
        return new DistilleryProcessEnvelope(new DistilleryStateCodec().decodeBase64(generation>0?value.substring("effects-v1:".length()):value),generation);
    }
}
