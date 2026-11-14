package com.junhsiun.musicplayer.mixin;

import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.20.1 及更早版本的配方 JSON 无法写 NBT，所以「唱片母版」的标记只能在
 * 合成结果写入结果槽时补上。
 *
 * <p>注入点是 {@code CraftingMenu.slotChangedCraftingGrid} 里唯一一次
 * {@code resultSlots.setItem(0, itemStack)}：工作台和玩家自带的 2x2 合成网格都会走这个
 * 静态方法，写进去的这份物品随后被复制后交给玩家。</p>
 *
 * <p>选 {@code setItem} 而不是方法末尾，是因为该方法在 {@code setItem} 之后立刻用同一个
 * {@code itemStack} 变量构造 {@code ClientboundContainerSetSlotPacket}。在 {@code setItem}
 * 处原地打标记，容器内容和发给客户端的同步包才会保持一致，否则客户端预览里会显示成普通唱片。</p>
 */
@Mixin(CraftingMenu.class)
public abstract class DiscMasterCraftingMixin {

    @Redirect(
            method = "slotChangedCraftingGrid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"
            )
    )
    private static void musicplayer$markCraftedDiscMaster(ResultContainer resultSlots, int slot, ItemStack stack) {
        MusicDiscHelper.markAsMaster(stack);
        resultSlots.setItem(slot, stack);
    }
}
