package me.arcanebrewery.arcaneBrewery.application.alchemy;
import java.io.*;
import java.util.*;
public final class EffectProfileCodec {
    public byte[] encode(EffectProfile p) {
        try {var b=new ByteArrayOutputStream();var o=new DataOutputStream(b);o.writeInt(0x41424550);o.writeInt(p.version());o.writeUTF(p.capability().name());o.writeUTF(p.safety().name());o.writeDouble(p.stability());o.writeInt(p.effects().size());
            for(var e:p.effects()){o.writeUTF(e.effect().name());o.writeInt(e.seconds());o.writeInt(e.amplifier());o.writeBoolean(e.sideEffect());}o.flush();return b.toByteArray();
        } catch(IOException e){throw new IllegalStateException(e);}
    }
    public EffectProfile decode(byte[] bytes) {
        if(bytes==null||bytes.length==0||bytes.length>4096) throw new IllegalArgumentException("invalid effect profile size");
        try {var i=new DataInputStream(new ByteArrayInputStream(bytes));if(i.readInt()!=0x41424550) throw new IllegalArgumentException("invalid effect profile header");int version=i.readInt();var cap=EffectCapabilities.valueOf(i.readUTF());var safety=EffectProfile.Safety.valueOf(i.readUTF());double stability=i.readDouble();int count=i.readInt();if(count<0||count>4) throw new IllegalArgumentException("invalid effect count");
            var effects=new ArrayList<EffectProfile.Entry>();for(int n=0;n<count;n++)effects.add(new EffectProfile.Entry(ProfileEffect.valueOf(i.readUTF()),i.readInt(),i.readInt(),i.readBoolean()));
            if(i.available()!=0) throw new IllegalArgumentException("trailing effect profile bytes");return new EffectProfile(version,cap,effects,safety,stability);
        } catch(IOException e){throw new IllegalArgumentException("malformed effect profile",e);}
    }
}
