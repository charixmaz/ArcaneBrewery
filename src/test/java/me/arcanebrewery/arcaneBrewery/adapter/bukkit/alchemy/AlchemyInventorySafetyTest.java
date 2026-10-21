package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyInventorySafetyTest {
    @Test void processingBlocksEveryTopInputAndStartAction() {
        for (int slot : new int[]{2,4,6,8}) for (ClickType click : ClickType.values())
            assertEquals(AlchemyInventoryPolicy.Decision.CANCEL,
                    AlchemyInventoryPolicy.click(true, slot, true, click, InventoryAction.PICKUP_ALL));
    }
    @Test void indirectActionsFromEitherInventoryAreAlwaysBlocked() {
        for (boolean top : new boolean[]{true, false}) {
            for (ClickType click : new ClickType[]{ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY,
                    ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND, ClickType.MIDDLE, ClickType.DROP, ClickType.CONTROL_DROP}) {
                assertEquals(AlchemyInventoryPolicy.Decision.CANCEL,
                        AlchemyInventoryPolicy.click(top, 2, false, click, InventoryAction.MOVE_TO_OTHER_INVENTORY));
            }
            assertEquals(AlchemyInventoryPolicy.Decision.CANCEL,
                    AlchemyInventoryPolicy.click(top, 2, false, ClickType.LEFT, InventoryAction.COLLECT_TO_CURSOR));
            assertEquals(AlchemyInventoryPolicy.Decision.CANCEL,
                    AlchemyInventoryPolicy.click(top, 2, false, ClickType.LEFT, InventoryAction.HOTBAR_SWAP));
        }
    }
    @Test void onlyIdleInputsAreEditableAndDragCannotTouchTop() {
        assertEquals(AlchemyInventoryPolicy.Decision.EDIT,
                AlchemyInventoryPolicy.click(true, 2, false, ClickType.LEFT, InventoryAction.PLACE_ALL));
        assertEquals(AlchemyInventoryPolicy.Decision.START,
                AlchemyInventoryPolicy.click(true, 8, false, ClickType.LEFT, InventoryAction.NOTHING));
        assertEquals(AlchemyInventoryPolicy.Decision.CANCEL,
                AlchemyInventoryPolicy.click(true, 3, false, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(AlchemyInventoryPolicy.Decision.PASS,
                AlchemyInventoryPolicy.click(false, 0, true, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(AlchemyInventoryPolicy.dragTouchesMachine(Set.of(2, 20), 9));
        assertFalse(AlchemyInventoryPolicy.dragTouchesMachine(Set.of(9, 20), 9));
    }
    @Test void baseSlotTakesOnlyOneFromCursorAndPreservesQuantity() {
        var cursor = TestItems.stack(Material.POTION, 3);
        var move = AlchemySlotTransfer.move(null, cursor, false, 1);
        assertEquals(1, move.slot().getAmount());
        assertEquals(2, move.cursor().getAmount());
        assertEquals(3, cursor.getAmount());
    }
    @Test void rightClickSplitsAndDepositsOneWithoutMutatingOriginals() {
        var stack = TestItems.stack(Material.BLAZE_POWDER, 5);
        var take = AlchemySlotTransfer.move(stack, null, true, 64);
        assertEquals(3, take.cursor().getAmount());
        assertEquals(2, take.slot().getAmount());
        assertEquals(5, stack.getAmount());
        var deposit = AlchemySlotTransfer.move(take.slot(), take.cursor(), true, 64);
        assertEquals(3, deposit.slot().getAmount());
        assertEquals(2, deposit.cursor().getAmount());
    }
    @Test void mergingSwappingAndFullSlotsPreserveAllItems() {
        var merge = AlchemySlotTransfer.move(TestItems.stack(Material.BLAZE_POWDER, 63),
                TestItems.stack(Material.BLAZE_POWDER, 4), false, 64);
        assertEquals(64, merge.slot().getAmount()); assertEquals(3, merge.cursor().getAmount());
        var swap = AlchemySlotTransfer.move(TestItems.stack(Material.AMETHYST_SHARD, 2),
                TestItems.stack(Material.BLAZE_POWDER, 4), false, 64);
        assertEquals(Material.BLAZE_POWDER, swap.slot().getType()); assertEquals(4, swap.slot().getAmount());
        assertEquals(Material.AMETHYST_SHARD, swap.cursor().getType()); assertEquals(2, swap.cursor().getAmount());
    }
}
