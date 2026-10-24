package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.logging.Logger;

/** API boundary fixture: deliberately NOT a TileState implementation. */
final class MachineFixture {
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type,BiFunction<String,Object[],Object> methods,Class<?>... extra) {
        var interfaces=new ArrayList<Class<?>>(); interfaces.add(type); interfaces.addAll(Arrays.asList(extra));
        return (T)Proxy.newProxyInstance(type.getClassLoader(),interfaces.toArray(Class<?>[]::new),(p,m,a) -> switch(m.getName()) {
            case "equals" -> p==a[0]; case "hashCode" -> System.identityHashCode(p); case "toString" -> type.getSimpleName();
            default -> methods.apply(m.getName(),a);
        });
    }
    static PersistentDataContainer data() { return TestItems.stack(Material.PAPER,1).getItemMeta().getPersistentDataContainer(); }
    final Map<String,Block> blocks=new HashMap<>();
    final Map<String,Material> materials=new HashMap<>();
    final Map<String,PersistentDataContainer> tiles=new HashMap<>();
    final Map<String,Chunk> chunks=new HashMap<>();
    boolean loaded=true;
    final World world=proxy(World.class,(name,args) -> switch(name) {
        case "getUID" -> new UUID(0,1); case "getName" -> "test"; case "isChunkLoaded" -> loaded;
        case "getChunkAt" -> chunk((int)args[0],(int)args[1]); default -> throw new UnsupportedOperationException(name);
    });
    final Plugin plugin=proxy(Plugin.class,(name,args) -> switch(name) {
        case "getName" -> "ArcaneBrewery"; case "getLogger" -> Logger.getLogger("machine-test"); case "isEnabled" -> true;
        default -> throw new UnsupportedOperationException(name);
    });
    Chunk chunk(int x,int z) {
        return chunks.computeIfAbsent(x+","+z,ignored -> {
            var data=data();
            return proxy(Chunk.class,(name,args) -> switch(name) {
                case "getPersistentDataContainer" -> data; case "getX" -> x; case "getZ" -> z; case "getWorld" -> world;
                default -> throw new UnsupportedOperationException(name);
            });
        });
    }
    Block block(int x,int y,int z) {
        String key=x+","+y+","+z;
        return blocks.computeIfAbsent(key,ignored -> proxy(Block.class,(name,args) -> switch(name) {
            case "getX" -> x; case "getY" -> y; case "getZ" -> z; case "getWorld" -> world;
            case "getType" -> materials.getOrDefault(key,Material.AIR);
            case "getChunk" -> chunk(x>>4,z>>4);
            case "getLocation" -> new Location(world,x,y,z);
            case "getRelative" -> {
                if (args.length==1) { var face=(BlockFace)args[0]; yield block(x+face.getModX(),y+face.getModY(),z+face.getModZ()); }
                yield block(x+(int)args[0],y+(int)args[1],z+(int)args[2]);
            }
            case "getState" -> {
                var data=tiles.computeIfAbsent(key,k -> data());
                yield proxy(BlockState.class,(method,values) -> switch(method) {
                    case "getBlock" -> block(x,y,z); case "getPersistentDataContainer" -> data; case "getType" -> materials.getOrDefault(key,Material.AIR);
                    default -> throw new UnsupportedOperationException(method);
                },PersistentDataHolder.class,InventoryHolder.class);
            }
            default -> throw new UnsupportedOperationException(name);
        }));
    }
    Block put(int x,int y,int z,Material material) { materials.put(x+","+y+","+z,material); return block(x,y,z); }
    Inventory inventory(Block core) {
        return proxy(Inventory.class,(name,args) -> switch(name) {
            case "getHolder" -> core==null?null:core.getState(); case "getViewers" -> List.of(); case "getSize" -> 9; case "isEmpty" -> true;
            default -> throw new UnsupportedOperationException(name);
        });
    }
    Player player(UUID id,boolean bypass) {
        return player(id,bypass,false);
    }
    Player player(UUID id,boolean bypass,boolean denyOpen) {
        var open=new AtomicReference<Inventory>(); var self=new Player[1];
        self[0]=proxy(Player.class,(name,args) -> switch(name) {
            case "getUniqueId" -> id; case "getName" -> "TestOwner"; case "hasPermission" -> bypass; case "sendMessage" -> null;
            case "openInventory" -> { if (!denyOpen) open.set((Inventory)args[0]); yield view(open.get(),self[0]); }
            case "getOpenInventory" -> view(open.get(),self[0]); case "closeInventory" -> { open.set(null); yield null; }
            default -> throw new UnsupportedOperationException(name);
        }); return self[0];
    }
    InventoryView view(Inventory inv,Player player) {
        return proxy(InventoryView.class,(name,args) -> switch(name) {
            case "getTopInventory" -> inv; case "getPlayer" -> player;
            default -> throw new UnsupportedOperationException(name);
        });
    }
}
