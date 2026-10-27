package me.arcanebrewery.arcaneBrewery;

import java.util.*;

/** Concise, implemented-only help topics; no network or recipe-state access. */
public final class BrewHelp {
    public static final List<String> TOPICS=List.of("getting-started","machines","recipes","commands");
    private BrewHelp() { }
    public static List<String> lines(String topic) {
        return switch (topic.toLowerCase(Locale.ROOT)) {
            case "getting-started" -> List.of(
                    "§5§lGetting Started§r",
                    "§7Obtain supported ingredients and reagents to unlock their Codex entries; material knowledge is not recipe discovery.§r",
                    "§7Wash supported held ingredients by right-clicking a water cauldron.§r",
                    "§7Essence Extractor → Cultivation Barrel → optional Arcane Infuser → Maturation Cask.§r",

                    "§7Extract with bottles and a washed ingredient; insert bases in a plain Barrel, close, then sneak-click for controls.§r",
                    "§7Infuse a completed base with one or two catalysts, or mature it directly.§r",
                    "§7Mature withdrawal creates a finished elixir and attempts private discovery registration.§r",
                    "§7Early cask withdrawal gives an Unfinished Elixir and never creates a discovery.§r",
                    "§7Elixirs create temporary saturation; excessive exposure causes overload and recovery.§r");
            case "machines" -> List.of(
                    "§5§lMachines§r",
                    "§bEssence Extractor: §fBrewing Stand directly above a Crafting Table.§r",

                    "§7Bottle slots 1–3; washed ingredients in slots 5–6; choose settings and click START.§r",

                    "§bCultivation Barrel: §fA plain Barrel holding canonical refined bases; sneak-click for culture, setpoint, duration and Start.§r",
                    "§bArcane Infuser: §fEnchanting Table, Crying Obsidian below, ≥1 horizontal Amethyst Block.§r",
                    "§7One cultivated base, Catalyst I and optional Catalyst II; click Start Infusion.§r",
                    "§bMaturation Cask: §fBarrel, adjacent [Brewery] sign and ≥12 matching wood stairs/slabs/planks/logs within 3×3×3.§r",
                    "§7Use the sign. Four lanes; click/shift-click a bottle to withdraw; no cask cleaning is required.§r",
                    "§7First valid use claims a machine. Owner-only, one viewer; automation is blocked for recognized machines.§r");
            case "recipes" -> List.of(
                    "§5§lRecipes§r",
                    "§f/brew discoveries [page] §7— your discoveries, secret by default.§r",
                    "§f/brew recipe <full fingerprint|shown id> §7— inspect only knowledge you may access.§r",
                    "§7Short IDs resolve across your complete accessible catalog; supply ≥6 unambiguous hex characters.§r",

                    "§7Rename accepts held or short-id; visibility uses full IDs. Rename is permanent and requires a final confirm.§r",

                    "§7Formulation, culture style and catalyst doses define identity; temperatures, wood and quality are batch data.§r");
            case "commands" -> List.of(
                    "§5§lCommands§r",
                    "§f/brew inspect §7— inspect the held elixir and its quality breakdown.§r",
                    "§f/brew guide §7— open the ingredient, catalyst, machine and recipe Codex.§r",
                    "§f/brew status §7— temporary saturation and recovery.§r",
                    "§f/brew value §7— calculated value of a supported held finished elixir.§r",
                    "§f/brew sell §7then §f/brew sell confirm §7— sale when enabled and an economy provider is available.§r",
                    "§7Economy is optional; brewing remains usable without it. Sales are disabled by default.§r",
                    "§f/brew discoveries §7and §f/brew recipe §7— browse your recipe knowledge.§r");
            default -> List.of("§5§lArcaneBrewery — Brewing & Alchemy§r",
                    "§f/brew inspect §7— inspect the held elixir and its quality breakdown.§r",
                    "§f/brew guide §7— open the visual Brewing Codex§r",
                    "§f/brew help getting-started §7— production and washing§r",
                    "§f/brew help machines §7— structures, inputs and ownership§r",
                    "§f/brew help recipes §7— discoveries and short IDs§r",
                    "§f/brew help commands §7— status, value and optional sales§r");
        };
    }
}
