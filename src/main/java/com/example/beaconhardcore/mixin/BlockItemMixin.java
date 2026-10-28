package com.example.beaconhardcore.mixin;

import com.example.beaconhardcore.BeaconHardcoreMod;
import com.example.beaconhardcore.BeaconOwnershipManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "useOn", at = @At("RETURN"))
    private void beaconhardcore$storeOwner(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        if (context.getLevel().isClientSide()) return;

        ItemStack stack = context.getItemInHand();
        if (!stack.is(Items.BEACON)) return;
        if (stack.getTag() == null) return;
        if (!stack.getTag().containsUUID(BeaconHardcoreMod.BEACON_OWNER_NBT)) return;

        if (context.getLevel() instanceof ServerLevel serverLevel) {
            BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
            BeaconOwnershipManager.setOwner(
                    serverLevel,
                    placePos,
                    stack.getTag().getUUID(BeaconHardcoreMod.BEACON_OWNER_NBT)
            );
        }
    }
}