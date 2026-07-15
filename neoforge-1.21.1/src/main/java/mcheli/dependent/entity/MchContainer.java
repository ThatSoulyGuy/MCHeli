package mcheli.dependent.entity;

import mcheli.dependent.registry.MchRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A cargo container — the 1.21.1 port of {@code MCH_EntityContainer}, reduced to its portable core: a placeable,
 * damageable storage entity holding a 54-slot (double-chest) inventory that opens the vanilla chest GUI on right-click.
 * It falls under gravity and settles on the ground; a player's melee hit breaks it, dropping its own item and spilling
 * its contents.
 *
 * <p>DEFERRED: the reference container is also a SLING LOAD an aircraft can hook and carry
 * ({@code MCH_IEntityCanRideAircraft}) — that needs the unported tow/hitch mechanic, so it is left for when that lands.
 */
public class MchContainer extends Entity implements MenuProvider {

    /** Double-chest capacity (reference {@code getSizeInventory() == 54}). */
    public static final int SIZE = 54;

    private final SimpleContainer inventory = new SimpleContainer(SIZE) {
        @Override public boolean stillValid(Player player) {
            return !MchContainer.this.isRemoved() && player.distanceToSqr(MchContainer.this) < 64.0;
        }
    };

    public MchContainer(EntityType<? extends MchContainer> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        super.tick();
        // Simple settle physics: fall under gravity until the ground stops it, with horizontal drag.
        Vec3 m = getDeltaMovement();
        if (!onGround()) {
            m = m.add(0.0, -0.04, 0.0);
        }
        move(MoverType.SELF, m);
        setDeltaMovement(getDeltaMovement().multiply(0.9, 0.98, 0.9));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            player.openMenu(this); // reference interactFirst -> openInventory (the vanilla chest GUI here)
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || !(source.getEntity() instanceof Player player)) {
            return false; // only a player's melee breaks it (reference gates on the "player" damage type)
        }
        if (!player.getAbilities().instabuild) {
            spawnAtLocation(new ItemStack(MchRegistries.CONTAINER_ITEM.get()));
        }
        dropContents();
        discard();
        return true;
    }

    private void dropContents() {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        this.inventory.clearContent();
    }

    @Override public Component getDisplayName() { return Component.translatable("entity.mcheli.container"); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return ChestMenu.sixRows(id, playerInventory, this.inventory);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.inventory.fromTag(tag.getList("Items", Tag.TAG_COMPOUND), this.registryAccess());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Items", this.inventory.createTag(this.registryAccess()));
    }

    @Override public boolean isPickable() { return !this.isRemoved(); }   // ray-pick target for right-click + attack
    @Override public boolean canBeCollidedWith() { return !this.isRemoved(); }
    @Override public boolean isPushable() { return true; }
}
