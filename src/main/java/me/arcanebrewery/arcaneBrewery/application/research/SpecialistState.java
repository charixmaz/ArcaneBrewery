package me.arcanebrewery.arcaneBrewery.application.research;
/** Output is computed and input consumed in the same snapshot at start; time only releases the locked output. */
public record SpecialistState(SpecialistType type,boolean built,long readyAt,int selection){
 public SpecialistState {if(type==null||readyAt<0||selection<0||selection>6||!built&&readyAt>0)throw new IllegalArgumentException("Invalid specialist state");}
 public String encode(){return "specialist-v1|"+type.name()+"|"+(built?1:0)+"|"+readyAt+"|"+selection;}
 public static SpecialistState decode(String text){var p=text.split("\\|",-1);if(p.length!=5||!p[0].equals("specialist-v1")||!p[2].matches("[01]"))throw new IllegalArgumentException("Unsupported specialist state");return new SpecialistState(SpecialistType.valueOf(p[1]),p[2].equals("1"),Long.parseLong(p[3]),Integer.parseInt(p[4]));}
 public SpecialistState complete(long now){return readyAt>0&&now>=readyAt?new SpecialistState(type,built,0,selection):this;}
}
