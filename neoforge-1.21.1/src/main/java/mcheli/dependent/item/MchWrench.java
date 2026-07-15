package mcheli.dependent.item;

import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * The maintenance wrench — the 1.21.1 port of {@code MCH_ItemWrench}. HOLD right-click while looking at a damaged
 * MCHeli vehicle to mend it: every 20 ticks it repairs 10 HP ({@link AbstractMchVehicle#repair}), spends 1 durability,
 * and clanks one of the {@code wrench1/2/3} sounds — exactly the reference {@code onUsingTick} loop. The targeted
 * vehicle's HP bar is drawn near the crosshair by {@link mcheli.dependent.client.hud.MchWrenchOverlay} (reference
 * {@code MCH_GuiWrench}). {@link #mouseOverVehicle} is the shared 4-block ray-pick used by both.
 */
public class MchWrench extends Item {

    /** Reach of the wrench's target ray-pick (reference {@code getMouseOver} uses 4 blocks). */
    private static final double REACH = 4.0;
    /** HP mended per repair pulse (reference {@code ac.repair(10)}). */
    private static final int REPAIR_PER_PULSE = 10;

    public MchWrench(Properties props) {
        super(props);
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BLOCK; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand); // begin the hold-to-repair action (reference setItemInUse)
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide || !(entity instanceof Player player) || remainingUseTicks % 20 != 0) {
            return; // reference repairs on `count % 20 == 0` (once per second), server-side
        }
        AbstractMchVehicle ac = mouseOverVehicle(player);
        if (ac != null && !ac.isDestroyed() && ac.getHp() > 0 && ac.repair(REPAIR_PER_PULSE)) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            SoundEvent snd = MchSounds.byName("wrench" + (1 + player.getRandom().nextInt(3)));
            if (snd != null) {
                level.playSound(null, ac.getX(), ac.getY(), ac.getZ(), snd, SoundSource.PLAYERS,
                    1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            }
        }
    }

    /** The MCHeli vehicle {@code player} is looking at within {@link #REACH}, resolving a struck part hitbox to its
     *  parent vehicle, or null. Shared by the repair tick and the client HP overlay. */
    public static AbstractMchVehicle mouseOverVehicle(Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(REACH));
        AABB box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
            e -> (e instanceof AbstractMchVehicle || e instanceof PartEntity<?>) && e.isPickable());
        if (hit != null) {
            Entity e = hit.getEntity();
            if (e instanceof PartEntity<?> part) {
                e = part.getParent();
            }
            if (e instanceof AbstractMchVehicle v) {
                return v;
            }
        }
        return null;
    }
}
