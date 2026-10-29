package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import java.util.*;

/** Visual slots are separate from the unchanged saved item indexes. */
public enum ProductionLayout {
 EXTRACTOR(Set.of(0,1,2,4,5),Map.of(3,19,6,21,7,23,8,25)),
 INFUSER(Set.of(2,4,6),Map.of(0,11,1,13,3,15,5,21,7,23,8,25)),
 CULTIVATION(Set.of(),Map.of(0,10,2,12,4,14,8,22)),
 CASK(Set.of(0,2,4,6),Map.of(1,9,3,11,5,13,7,15,8,22));
 public static final int SIZE=27;
 private final Set<Integer> inputs;private final Map<Integer,Integer> controls;
 ProductionLayout(Set<Integer> inputs,Map<Integer,Integer> controls){this.inputs=Set.copyOf(inputs);this.controls=Map.copyOf(controls);}
 public Set<Integer> inputs(){return inputs;}
 public Map<Integer,Integer> controls(){return controls;}
 public int logical(int visible){if(inputs.contains(visible))return visible;return controls.entrySet().stream().filter(e->e.getValue()==visible).mapToInt(e->e.getKey()).findFirst().orElse(-1);}
 private static ItemStack pane(){var i=new ItemStack(Material.BLACK_STAINED_GLASS_PANE);var m=i.getItemMeta();DisplayOnlyItems.mark(m);m.setDisplayName(" ");PresentationStyle.apply(m);i.setItemMeta(m);return i;}
 public void frame(Inventory gui){if(gui.getSize()!=SIZE)throw new IllegalArgumentException("Wrong production GUI size");for(int i=0;i<SIZE;i++)if(!inputs.contains(i))gui.setItem(i,pane());}
 public void relocate(Inventory gui){var saved=new HashMap<Integer,ItemStack>();controls.forEach((old,visible)->saved.put(visible,gui.getItem(old)));controls.keySet().forEach(i->gui.setItem(i,pane()));saved.forEach(gui::setItem);}
 public static void label(Inventory gui,int slot,Material icon,String title,String... lines){var i=new ItemStack(icon);var m=i.getItemMeta();DisplayOnlyItems.mark(m);m.setDisplayName("§b§l"+title+"§r");m.setLore(Arrays.stream(lines).map(s->"§f"+s+"§r").toList());PresentationStyle.apply(m);i.setItemMeta(m);gui.setItem(slot,i);}
}
