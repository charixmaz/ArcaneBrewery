package me.arcanebrewery.arcaneBrewery.application.distillery;
import java.util.Objects;
/** Captures the effects policy at extraction start without changing legacy process/fingerprint encoding. */
public record DistilleryProcessEnvelope(DistilleryBatchState state,int effectGeneration,int qualityGeneration) {
    public DistilleryProcessEnvelope(DistilleryBatchState state,int effectGeneration){this(state,effectGeneration,2);}
    public DistilleryProcessEnvelope {if(qualityGeneration!=2&&qualityGeneration!=3)throw new IllegalArgumentException("unsupported quality generation");Objects.requireNonNull(state);if(effectGeneration<0||effectGeneration>2)throw new IllegalArgumentException("unsupported effects generation");}
    public String encode(){return(qualityGeneration==3?"quality-v3:":"")+(effectGeneration>0?"effects-v"+effectGeneration+":":"")+new DistilleryStateCodec().encodeBase64(state);}
    public static DistilleryProcessEnvelope decode(String value){
        int quality=value.startsWith("quality-v3:")?3:2;
        if(quality==3)value=value.substring("quality-v3:".length());
        int generation=value.startsWith("effects-v2:")?2:value.startsWith("effects-v1:")?1:0;
        return new DistilleryProcessEnvelope(new DistilleryStateCodec().decodeBase64(generation>0?value.substring("effects-v1:".length()):value),generation,quality);
    }
}
