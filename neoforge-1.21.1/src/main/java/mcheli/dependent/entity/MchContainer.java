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
 * It falls under gravity and settles on the ground; a few quick player melee hits (one in creative) break it, dropping
 * its own item and spilling its contents.
 *
 * <p>DEFERRED (see DEFERRED.md): the reference container can also ride an aircraft's rack
 * ({@code MCH_IEntityCanRideAircraft}, {@code AddRack}/{@code RideRack}) — that needs the unported entity-rack system,
 * so it is left for when that lands.
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
        if (this.damageTaken > 0) {
            this.damageTaken--; // reference onUpdate :178-180: the break meter drains between hits
        }
        // Simple settle physics: fall under gravity until the ground stops it, with horizontal drag.
        Vec3 m = getDeltaMovement();
        if (!onGround()) {
            m = m.add(0.0, -0.04, 0.0);
        }
        move(MoverType.SELF, m);
        setDeltaMovement(getDeltaMovement().multiply(0.9, 0.98, 0.9));
        if (!this.level().isClientSide && mcheli.dependent.config.MchConfig.SERVER.collisionDestroyBlock()
                && net.neoforged.neoforge.event.EventHooks.canEntityGrief(this.level(), this)) {
            sweepSnowAndLilyPads();
        }
    }

    /** Reference onUpdate :303-317 (under {@code Collision_DestroyBlock}): four corner columns at ±0.4, two levels; a
     *  snow layer vanishes, a lily pad breaks with its drop. */
    private void sweepSnowAndLilyPads() {
        for (int l = 0; l < 4; l++) {
            int bx = net.minecraft.util.Mth.floor(getX() + ((l % 2) - 0.5) * 0.8);
            int bz = net.minecraft.util.Mth.floor(getZ() + ((l / 2) - 0.5) * 0.8);
            for (int k = 0; k < 2; k++) {
                net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(bx, net.minecraft.util.Mth.floor(getY()) + k, bz);
                if (!this.level().isLoaded(p)) {
                    continue;
                }
                net.minecraft.world.level.block.state.BlockState s = this.level().getBlockState(p);
                if (s.is(net.minecraft.world.level.block.Blocks.SNOW)) {
                    this.level().removeBlock(p, false);
                } else if (s.is(net.minecraft.world.level.block.Blocks.LILY_PAD)) {
                    this.level().destroyBlock(p, true, this);
                }
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            player.openMenu(this); // reference interactFirst -> openInventory (the vanilla chest GUI here)
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /**
     * Reference {@code MCH_EntityContainer.attackEntityFrom} (:107-139): only a player's melee hit counts. Each hit adds
     * {@code damage * 20} to a damage meter that drains 1 per tick ({@link #tick}); a creative hit, or the meter passing
     * 40 (about three quick punches), breaks the container, dropping its own item (survival only) and its contents.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        amount = mcheli.dependent.config.MchServerRules.applyDamageByExternal(this, source, amount); // reference :113
        if (!(source.getEntity() instanceof Player player)
                || !source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) {
            return false;
        }
        net.minecraft.sounds.SoundEvent hit = mcheli.dependent.registry.MchSounds.byName("hit");
        if (hit != null) {
            this.level().playSound(null, getX(), getY(), getZ(), hit, net.minecraft.sounds.SoundSource.NEUTRAL,
                1.0F, 1.3F);
        }
        this.damageTaken += (int) (amount * 20.0F);
        markHurt();
        boolean creative = player.getAbilities().instabuild;
        if (creative || this.damageTaken > 40) {
            if (!creative) {
                spawnAtLocation(new ItemStack(MchRegistries.CONTAINER_ITEM.get()));
            }
            dropContents();
            discard();
        }
        return true;
    }

    /** Reference {@code damageTaken} (DataWatcher 19): the break meter, server-side here (the reference synced it only
     *  for the client hit wobble). */
    private int damageTaken;

    /** Reference {@code isImmuneToFire = true}. */
    @Override
    public boolean fireImmune() {
        return true;
    }

    /** Reference {@code renderDistanceWeight = 2.0} (the 1.7.10 rule {@code avgEdge * 64 * weight}), still scaled by the
     *  vanilla Entity Distance option. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        double d = this.getBoundingBox().getSize();
        if (Double.isNaN(d)) {
            d = 1.0;
        }
        d *= 64.0 * 2.0 * getViewScale();
        return distSq < d * d;
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
