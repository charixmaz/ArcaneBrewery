package me.arcanebrewery.arcaneBrewery.domain.discovery;
/** Color-only names; no obfuscation, styling, RGB, controls or raw section codes. */
public final class NameColors {
 private NameColors() { }
 public static String prepare(String raw,boolean allowed){
  if(raw==null||raw.indexOf('§')>=0)throw new IllegalArgumentException("Raw formatting is not allowed");
  var out=new StringBuilder();var visible=new StringBuilder();
  for(int i=0;i<raw.length();i++){
   char c=raw.charAt(i);
   if(c=='&'&&i+1<raw.length()){
    char code=Character.toLowerCase(raw.charAt(i+1));
    if("0123456789abcdef".indexOf(code)>=0){if(!allowed)throw new IllegalArgumentException("Color names require arcanebrewery.rename.colors");out.append('§').append(code);i++;continue;}
    if("klmnorx".indexOf(code)>=0)throw new IllegalArgumentException("Only &0–&f colors are allowed");
   }
   out.append(c);visible.append(c);
  }
  new DrinkName(visible.toString());
  if(out.length()>240)throw new IllegalArgumentException("Too many color codes");
  return out.toString();
 }
}
