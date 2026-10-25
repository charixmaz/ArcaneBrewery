package me.arcanebrewery.arcaneBrewery.domain.drink;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.io.*;
import java.security.*;
import java.util.*;

/** Domain-separated V2 discovery encoding. V1 exact-process encoding remains unchanged. */
public final class FormulationIdentity {
    private FormulationIdentity() { }
    public static DrinkFingerprint from(ProcessProfile p) {
        Objects.requireNonNull(p);
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeInt(2);out.writeUTF("ArcaneBrewery/formulation");
            amounts(out,p.ingredientRatios());out.writeUTF(p.fermentation().yeastOrCatalyst());
            amounts(out,p.alchemicalAdditives());out.flush();
            return new DrinkFingerprint(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
        } catch(IOException | NoSuchAlgorithmException e) { throw new IllegalStateException("Cannot encode formulation identity",e); }
    }
    private static void amounts(DataOutputStream out,Map<String,Double> values) throws IOException {
        out.writeInt(values.size());
        for(var entry:new TreeMap<>(values).entrySet()) {out.writeUTF(entry.getKey());out.writeDouble(entry.getValue());}
    }
}
