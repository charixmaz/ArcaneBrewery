package me.arcanebrewery.arcaneBrewery.application.distillery;
import java.util.Objects;
/** Captures the effects policy at extraction start without changing legacy process/fingerprint encoding. */
public record DistilleryProcessEnvelope(DistilleryBatchState state,int effectGeneration) {
    private static final String PREFIX="effects-v1:";
    public DistilleryProcessEnvelope {Objects.requireNonNull(state);if(effectGeneration<0||effectGeneration>1)throw new IllegalArgumentException("unsupported effects generation");}
    public String encode(){return(effectGeneration==1?PREFIX:"")+new DistilleryStateCodec().encodeBase64(state);}
    public static DistilleryProcessEnvelope decode(String value){boolean current=value.startsWith(PREFIX);return new DistilleryProcessEnvelope(new DistilleryStateCodec().decodeBase64(current?value.substring(PREFIX.length()):value),current?1:0);}
}
