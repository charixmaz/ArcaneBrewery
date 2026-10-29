package me.arcanebrewery.arcaneBrewery.guide;

import me.arcanebrewery.arcaneBrewery.application.research.SpecialistType;
import org.bukkit.Material;
import java.util.List;
import java.util.Locale;

/** Display-only structure diagrams. Vertical inventory slots represent blocks above one another. */
public final class MachineBuildLayout {
    private MachineBuildLayout() { }
    public record Part(int slot, Material icon, String title, List<String> lines) { }
    private static Part part(int slot, Material icon, String title, String... lines) {
        return new Part(slot, icon, title, List.of(lines));
    }
    public static List<Part> parts(Material icon) {
        for (var t : SpecialistType.values()) if (t.core.equals(icon.name())) return List.of(
            part(4, Material.BOOK, "Build · Front View", "Place the two blocks directly above one another."),
            part(13, icon, "Top · " + GuideCatalog.label(t.core.toLowerCase(Locale.ROOT)), "Sneak-right-click this core to claim and construct.", "After construction, right-click to open."),
            part(22, Material.valueOf(t.foundation), "Below · " + GuideCatalog.label(t.foundation.toLowerCase(Locale.ROOT)), "Foundation must touch the core directly."));
        return switch (icon) {
            case BREWING_STAND -> List.of(
                part(4, Material.BOOK, "Build · Front View", "Two blocks, one vertical column."),
                part(13, Material.BREWING_STAND, "Top · Brewing Stand", "Right-click either block to open the extractor."),
                part(22, Material.CRAFTING_TABLE, "Below · Crafting Table", "Directly beneath the Brewing Stand."));
            case ENCHANTING_TABLE -> List.of(
                part(4, Material.BOOK, "Build · Front View", "The Amethyst Block touches a horizontal side of the table.", "It may be on any of the four sides."),
                part(13, Material.ENCHANTING_TABLE, "Core · Enchanting Table", "Right-click to open the Arcane Infuser."),
                part(14, Material.AMETHYST_BLOCK, "Side · Amethyst Block", "Same height as the Enchanting Table."),
                part(22, Material.CRYING_OBSIDIAN, "Below · Crying Obsidian", "Directly below the Enchanting Table."));
            case BARREL -> List.of(
                part(4, Material.BOOK, "Build · Single Block", "A plain Barrel; no sign or surrounding cask structure."),
                part(13, Material.BARREL, "Cultivation Barrel", "Insert refined bases, close, then sneak-right-click.", "Select settings and Start Cultivation."));
            case CAULDRON -> List.of(
                part(4, Material.BOOK, "Prepare · Water Cauldron", "Fill the cauldron with water first."),
                part(13, Material.WATER_BUCKET, "Add Water", "One water level is used per wash."),
                part(22, Material.CAULDRON, "Wash Here", "Hold a supported ingredient and right-click.", "A washed result needs room in your inventory."));
            case OAK_STAIRS -> List.of(
                part(4, Material.BOOK, "Maturation Cask · Flexible Structure", "This build uses a wood count rather than one fixed pattern."),
                part(13, Material.BARREL, "Core · Barrel", "Place at least 12 matching wood components", "inside the 3 × 3 × 3 area centred on the Barrel.", "Matching planks, stairs, slabs and logs count."),
                part(22, Material.OAK_SIGN, "Adjacent Sign · [Brewery]", "First line must be [Brewery].", "Right-click the sign to open the cask.", "Experiment with harvest timing; no readiness indicator."));
            default -> throw new IllegalArgumentException("No build guide for " + icon);
        };
    }
}
