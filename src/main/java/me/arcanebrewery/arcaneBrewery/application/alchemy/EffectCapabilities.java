package me.arcanebrewery.arcaneBrewery.application.alchemy;
/** Capability boundary: future specialists require separate researched/constructed implementations. */
public enum EffectCapabilities {
    STARTER_INFUSER;
    public record Cap(int seconds,int amplifier) { }
    public Cap cap(ProfileEffect effect) {
        return switch(effect) {
            case HASTE,SPEED,JUMP_BOOST -> new Cap(60,1);
            case STRENGTH -> new Cap(30,0);
            case RESISTANCE,DOLPHINS_GRACE -> new Cap(25,0);
            case REGENERATION -> new Cap(20,0);
            case FIRE_RESISTANCE,SLOW_FALLING -> new Cap(60,0);
            case NIGHT_VISION,WATER_BREATHING -> new Cap(90,0);
            case INVISIBILITY,ABSORPTION -> new Cap(45,0);
            case LEVITATION -> new Cap(6,0);
            case TELEPORTATION -> new Cap(1,0);
            case HALLUCINATION -> new Cap(8,0);
            case CURSE,SLOWNESS -> new Cap(12,0);
            case WITHER -> new Cap(5,0);
            case HUNGER -> new Cap(20,0);
            case WEAKNESS -> new Cap(10,0);
            case NONE -> new Cap(0,0);
        };
    }
    public int maximumBenefits() {return 2;}
}
