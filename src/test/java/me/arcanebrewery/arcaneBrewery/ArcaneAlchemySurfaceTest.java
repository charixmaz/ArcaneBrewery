package me.arcanebrewery.arcaneBrewery;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.CodexBook;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ArcaneAlchemySurfaceTest {
 @Test void codexBookUsesStableMarkerAndCannotBeImitatedByName(){var item=TestItems.stack(Material.ENCHANTED_BOOK,1);var meta=item.getItemMeta();meta.setDisplayName("ArcaneAlchemy Codex");item.setItemMeta(meta);assertFalse(CodexBook.isBook(item));meta.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","codex_book_v1"),PersistentDataType.INTEGER,1);item.setItemMeta(meta);assertTrue(CodexBook.isBook(item.clone()));}
 @Test void commandSurfaceUsesGuideAndAccessWithoutCodex(){assertTrue(DiscoveryCommand.PLAYER_COMMANDS.contains("guide"));assertTrue(DiscoveryCommand.PLAYER_COMMANDS.contains("access"));assertFalse(DiscoveryCommand.PLAYER_COMMANDS.contains("codex"));}
 @Test void descriptorExposesFullNameAndShortAlias()throws Exception{var y=new YamlConfiguration();try(var input=getClass().getResourceAsStream("/plugin.yml")){y.loadFromString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}assertEquals("ArcaneAlchemy",y.getString("name"));assertTrue(y.getStringList("commands.arcanealchemy.aliases").contains("al"));assertFalse(y.contains("commands.codex"));}
}
