package me.arcanebrewery.arcaneBrewery.domain.process;

/** Explicit supported styles. Arbitrary setpoints within a style do not create recipes. */
public enum BrewingCulture {
    WILD("arcanebrewery:wild_yeast",22,60,1),
    LUMINOUS("arcanebrewery:luminous_culture",28,90,1.2),
    QUIET("arcanebrewery:quiet_culture",16,120,.75);
    private final String id;
    private final int temperature, seconds;
    private final double potency;
    BrewingCulture(String id,int temperature,int seconds,double potency) {
        this.id=id;this.temperature=temperature;this.seconds=seconds;this.potency=potency;
    }
    public String id() { return id; }
    public int temperature() { return temperature; }
    public int seconds() { return seconds; }
    public double potency() { return potency; }
    public static BrewingCulture of(String id) {
        for(var c:values()) if(c.id.equals(id)) return c;
        throw new IllegalArgumentException("Unsupported cultivation culture: "+id);
    }
}
