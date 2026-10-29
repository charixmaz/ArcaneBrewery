package me.arcanebrewery.arcaneBrewery.domain.discovery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NameColorsTest {
 @Test void permissionIsRequired(){assertThrows(IllegalArgumentException.class,()->NameColors.prepare("&3Moon",false));assertEquals("Moon",NameColors.prepare("Moon",false));}
 @Test void colorsOnlyAndVisibleNameRequired(){assertEquals("§3§cMoon",NameColors.prepare("&3&cMoon",true));for(var text:new String[]{"&kMoon","&lMoon","&x123456","§cMoon","&3","a\nb"})assertThrows(IllegalArgumentException.class,()->NameColors.prepare(text,true));}
 @Test void permanentNameStillCannotBeChangedTwice(){var d=DiscoveryFixtures.discovery().rename(DiscoveryFixtures.OWNER,"&3Moon",true);assertEquals("§3Moon",d.displayName().value());assertThrows(IllegalStateException.class,()->d.rename(DiscoveryFixtures.OWNER,"&cOther",true));}
}
