package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.application.aging.AgingResult;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.consumption.DrinkConsumptionProfile;
import me.arcanebrewery.arcaneBrewery.application.consumption.DrinkStrengthCalculator;
import me.arcanebrewery.arcaneBrewery.application.economy.DrinkValue;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class FinalDrinkMetadata {
    public static final int VERSION = 4;
    private final String namespace;
    private final DrinkStrengthCalculator strength = new DrinkStrengthCalculator();

    public FinalDrinkMetadata(JavaPlugin plugin) {
        this(plugin.getName().toLowerCase(Locale.ROOT));
    }

    public FinalDrinkMetadata(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    public void write(ItemMeta meta, AgingResult result, AlchemyResult alchemy, DrinkValue value) {
        var data = meta.getPersistentDataContainer();
        data.set(key("drink_format"), PersistentDataType.INTEGER, VERSION);
        data.set(key("drink_fingerprint"), PersistentDataType.STRING, result.simulation().fingerprint().value());
        data.set(key("drink_quality"), PersistentDataType.DOUBLE, result.simulation().quality().overallScore());
        data.set(key("drink_contamination"), PersistentDataType.DOUBLE, result.simulation().quality().contamination());
        data.set(key("drink_magical_potency"), PersistentDataType.DOUBLE, result.simulation().quality().magicalPotency());
        data.set(key("drink_strength"), PersistentDataType.DOUBLE, strength.calculate(result.process()));
        data.set(key("drink_alchemy_effect"), PersistentDataType.STRING, alchemy.effect().name());
        data.set(key("drink_alchemy_affinity"), PersistentDataType.DOUBLE, alchemy.affinity());
        data.set(key("drink_alchemy_potency"), PersistentDataType.DOUBLE, alchemy.potency());
        data.set(key("drink_alchemy_instability"), PersistentDataType.DOUBLE, alchemy.instability());
        data.set(key("drink_alchemy_dangerous"), PersistentDataType.BYTE, (byte) (alchemy.dangerousReaction() ? 1 : 0));
        data.set(key("drink_value_version"), PersistentDataType.INTEGER, value.version());
        data.set(key("drink_value_units"), PersistentDataType.DOUBLE, value.units());
        data.set(key("drink_value_quality"), PersistentDataType.DOUBLE, value.qualityComponent());
        data.set(key("drink_value_process"), PersistentDataType.DOUBLE, value.processComponent());
        data.set(key("drink_value_alchemy"), PersistentDataType.DOUBLE, value.alchemyComponent());
        data.set(key("drink_value_contamination_penalty"), PersistentDataType.DOUBLE, value.contaminationPenalty());
        if(result.assessment()!=null) data.set(key("drink_quality_breakdown"),PersistentDataType.STRING,String.join("\n",result.assessment().breakdown()));
        data.set(key("drink_stage"), PersistentDataType.STRING, "FINISHED");
    }

    public void maturation(ItemMeta meta,boolean mature,boolean poisonous,long target,long elapsed) {
        var data=meta.getPersistentDataContainer();
        data.set(key("drink_stage"),PersistentDataType.STRING,mature?"FINISHED":"UNFINISHED");
        data.set(key("drink_premature_poison"),PersistentDataType.BYTE,(byte)(poisonous?1:0));
        data.set(key("drink_maturity_target"),PersistentDataType.LONG,target);
        data.set(key("drink_harvest_seconds"),PersistentDataType.LONG,elapsed);
    }
    public boolean unfinished(ItemMeta meta) { return "UNFINISHED".equals(meta.getPersistentDataContainer().get(key("drink_stage"),PersistentDataType.STRING)); }
    public java.util.List<String> qualityBreakdown(ItemMeta meta) {
        String text=meta.getPersistentDataContainer().get(key("drink_quality_breakdown"),PersistentDataType.STRING);
        return text==null?java.util.List.of("No factor breakdown recorded for this older bottle."):java.util.Arrays.stream(text.split("\\n")).map(line -> line.replaceAll(
            "perfect window [0-9]+[–-][0-9]+ s at 18 C", "compare harvest times and record successful batches")).toList();
    }
    public boolean prematurePoison(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(key("drink_premature_poison"),PersistentDataType.BYTE,(byte)0)==1;
    }
    public Optional<Long> maturityTarget(ItemMeta meta) { return Optional.ofNullable(meta.getPersistentDataContainer().get(key("drink_maturity_target"),PersistentDataType.LONG)); }
    public Optional<Long> harvestSeconds(ItemMeta meta) { return Optional.ofNullable(meta.getPersistentDataContainer().get(key("drink_harvest_seconds"),PersistentDataType.LONG)); }

    public void producer(ItemMeta meta,java.util.UUID player,String cachedName,me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile p) {
        var data=meta.getPersistentDataContainer();
        data.set(key("drink_producer_uuid"),PersistentDataType.STRING,player.toString());
        data.set(key("drink_producer_name"),PersistentDataType.STRING,cachedName);
        data.set(key("drink_formulation_v2"),PersistentDataType.STRING,me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity.from(p).value());
        data.set(key("drink_maturation_wood"),PersistentDataType.STRING,p.aging().woodType());
    }
    public Optional<java.util.UUID> producer(ItemMeta meta) {
        String value=meta.getPersistentDataContainer().get(key("drink_producer_uuid"),PersistentDataType.STRING);
        try { return value==null?Optional.empty():Optional.of(java.util.UUID.fromString(value)); }
        catch(IllegalArgumentException e) { return Optional.empty(); }
    }
    public boolean mayRenameHeld(ItemMeta meta,java.util.UUID actor) {
        return readConsumption(meta).isPresent() && producer(meta).filter(actor::equals).isPresent() && formulation(meta).isPresent();
    }
    public Optional<me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint> formulation(ItemMeta meta) {
        String value=meta.getPersistentDataContainer().get(key("drink_formulation_v2"),PersistentDataType.STRING);
        return value!=null && value.matches("[0-9a-f]{64}")?Optional.of(new me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint(value)):Optional.empty();
    }
    public String producerLabel(ItemMeta meta) {
        String name=meta.getPersistentDataContainer().get(key("drink_producer_name"),PersistentDataType.STRING);
        return name!=null && name.matches("[A-Za-z0-9_]{1,16}")?name:producer(meta).map(Object::toString).orElse("Unknown");
    }
    public Optional<String> wood(ItemMeta meta) { return Optional.ofNullable(meta.getPersistentDataContainer().get(key("drink_maturation_wood"),PersistentDataType.STRING)); }

    public Optional<DrinkConsumptionProfile> readConsumption(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        Integer version = data.get(key("drink_format"), PersistentDataType.INTEGER);
        String stage = data.get(key("drink_stage"), PersistentDataType.STRING);
        if (version == null || stage == null) return Optional.empty();
        if ((version < 2 || version > VERSION) || !("FINISHED".equals(stage)||"UNFINISHED".equals(stage))) return Optional.empty();

        Double drinkStrength = data.get(key("drink_strength"), PersistentDataType.DOUBLE);
        Double quality = data.get(key("drink_quality"), PersistentDataType.DOUBLE);
        Double contamination = data.get(key("drink_contamination"), PersistentDataType.DOUBLE);
        Double magicalPotency = data.get(key("drink_magical_potency"), PersistentDataType.DOUBLE);
        String fingerprint = data.get(key("drink_fingerprint"), PersistentDataType.STRING);

        if (drinkStrength == null || quality == null || contamination == null
                || magicalPotency == null || fingerprint == null || fingerprint.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new DrinkConsumptionProfile(
                    drinkStrength, quality, contamination, magicalPotency));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    public Optional<AlchemyResult> readAlchemy(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        Integer version = data.get(key("drink_format"), PersistentDataType.INTEGER);
        if (version == null || version < 3) return Optional.empty();

        String effect = data.get(key("drink_alchemy_effect"), PersistentDataType.STRING);
        Double affinity = data.get(key("drink_alchemy_affinity"), PersistentDataType.DOUBLE);
        Double potency = data.get(key("drink_alchemy_potency"), PersistentDataType.DOUBLE);
        Double instability = data.get(key("drink_alchemy_instability"), PersistentDataType.DOUBLE);
        Byte dangerous = data.get(key("drink_alchemy_dangerous"), PersistentDataType.BYTE);

        if (effect == null || affinity == null || potency == null || instability == null || dangerous == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new AlchemyResult(
                    AlchemyEffect.valueOf(effect),
                    affinity,
                    potency,
                    instability,
                    dangerous != 0
            ));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    public Optional<DrinkValue> readValue(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        Integer version = data.get(key("drink_format"), PersistentDataType.INTEGER);
        if (version == null || version < 4) return Optional.empty();

        Integer valueVersion = data.get(key("drink_value_version"), PersistentDataType.INTEGER);
        Double units = data.get(key("drink_value_units"), PersistentDataType.DOUBLE);
        Double quality = data.get(key("drink_value_quality"), PersistentDataType.DOUBLE);
        Double process = data.get(key("drink_value_process"), PersistentDataType.DOUBLE);
        Double alchemy = data.get(key("drink_value_alchemy"), PersistentDataType.DOUBLE);
        Double penalty = data.get(key("drink_value_contamination_penalty"), PersistentDataType.DOUBLE);
        if (valueVersion == null || units == null || quality == null
                || process == null || alchemy == null || penalty == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new DrinkValue(
                    valueVersion, units, quality, process, alchemy, penalty));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

}
