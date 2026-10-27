package me.arcanebrewery.arcaneBrewery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReleaseCommandSurfaceTest {
 @Test void sharingIsAbsentFromCompletionAndEveryHelpTopic(){assertFalse(DiscoveryCommand.PLAYER_COMMANDS.contains("share"));assertFalse(DiscoveryCommand.PLAYER_COMMANDS.contains("grant"));assertFalse(DiscoveryCommand.PLAYER_COMMANDS.contains("revoke"));assertFalse(BrewHelp.TOPICS.contains("sharing"));for(var topic:BrewHelp.TOPICS){var text=BrewHelp.lines(topic).toString();assertFalse(text.contains("/brew share"));assertFalse(text.contains("/brew revoke"));assertFalse(text.contains("/brew grant"));assertFalse(text.toLowerCase().contains("contamination"));}}
}
