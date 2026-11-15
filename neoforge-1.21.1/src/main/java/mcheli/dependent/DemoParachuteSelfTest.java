package mcheli.dependent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import mcheli.MCHeli;
import mcheli.agnostic.parachute.ParachuteFlight;
import mcheli.dependent.entity.MchParachute;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The parachute (reference {@code parachute/MCH_EntityParachute}, {@code MCH_ItemParachute}), headless. The
 * synchronous checks run in {@link #start}; the flights run side by side, each a probe in its own corner of the area,
 * and the test ends when the last one has its verdict. The first unmet requirement is the FAIL reason.
 * <ul>
 *   <li><b>P0</b> {@code MchParachute.DATA_TYPE} is synced-data id 8.</li>
 *   <li><b>P1</b> registration: {@code mcheli:parachute} is 1.5 x 0.6, tracked to 13 chunks with vanilla's 3-tick
 *       updates and velocity, saved and summonable; the item stacks to 1; the recipe is the shaped
 *       {@code WWW / S S / _W_} of white wool and string making one.</li>
 *   <li><b>P2</b> an airborne use spends the item and opens one type-1 parachute 3.5 above the player (and half a
 *       block off on X and Z) with his yaw and fall distance; his own fall distance is cleared; next tick he hangs
 *       under it, feet 0.5 below its box and 0.1 forward.</li>
 *   <li><b>P3</b> on the ground or while riding the item is spent and nothing opens (kept reference quirk 1); in
 *       creative the item is kept.</li>
 *   <li><b>P4</b> a type-3 drop with a pig settles at 0.3 a tick ({@code mY} -0.27, fall distance near 2), is removed
 *       on its 6th tick on the ground, and leaves the pig free and unhurt.</li>
 *   <li><b>P5</b> a riderless ejection seat launched at 2 a tick peaks about 26.4 up at its 30th tick and opens at
 *       the end of its 35th.</li>
 *   <li><b>P6</b> a player's W steers it along his yaw (yaw 90: toward -X) up to the 0.35 cap with the multiplier
 *       ramping; with no input the multiplier decays to 0.07, and S adds nothing; it takes the rider's yaw.</li>
 *   <li><b>P7</b> sneaking drops the player; the parachute falls on, turning at most 20 degrees a tick. A riderless
 *       parachute drifting +Z at yaw 90 turns toward 0, not 180 (the fixed reference heading bug).</li>
 *   <li><b>P8</b> an ejection seat with a pig landing in a 5x5x3 pool leaves the pig in a boat at the probe cell
 *       corner on the water, facing the parachute's heading; a type-3 drop, or a 3x3x3 pool, makes no boat; every
 *       parachute that touches water is removed.</li>
 *   <li><b>P9</b> NBT: {@code ParachuteModelType} round-trips, the steering multiplier does not.</li>
 *   <li><b>P10</b> a killed rider stays aboard through its death and the parachute follows it within 2 ticks of its
 *       removal; two overlapping parachutes push apart; it cannot be hurt.</li>
 *   <li><b>P11</b> it never rides by an unforced mount (a forced one still works); a minecart running at 0.3 a tick
 *       past a parachute with a pig, the cart's 0.2 pickup margin over the parachute's box for several ticks, never
 *       takes it, and the parachute lands as usual (the 1.21 cart picks up any pushable entity; the 1.7.10 cart took
 *       only living ones).</li>
 * </ul>
 * The rendering (types 1 to 3, the culling box, the smoke trail) and the creative tabs are client-only (a runClient
 * check).
 */
public final class DemoParachuteSelfTest extends MchSelfTest {

    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;

    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Probe> probes = new ArrayList<>();
    private final List<String> verdicts = new ArrayList<>();

    @Override
    protected int timeoutTicks() {
        return 700;
    }

    @Override
    protected void start() {
        try {
            testIds();
            testRegistration();
            testRefusals();
            testNbt();
            this.probes.add(new AirborneUse());
            this.probes.add(new Descent());
            this.probes.add(new SeatLaunch());
            this.probes.add(new Steering());
            this.probes.add(new RiderlessHeading());
            this.probes.add(new WaterLanding("boat", 20, 10, 2, ParachuteFlight.TYPE_SEAT, true));
            this.probes.add(new WaterLanding("type3", 20, 25, 2, ParachuteFlight.TYPE_DROP, false));
            this.probes.add(new WaterLanding("small", 35, 10, 1, ParachuteFlight.TYPE_SEAT, false));
            this.probes.add(new RiderDeath());
            this.probes.add(new PushApart());
            this.probes.add(new CartPass());
            LOG.info("[PARACHUTE SELF-TEST] P0 id, P1 registration, P3 refusals, P9 NBT and P10 hurt OK; {} flights",
                this.probes.size());
        } catch (IllegalStateException e) {
            fail("{}", e.getMessage());
        }
    }

    @Override
    protected void tick() {
        try {
            for (Iterator<Probe> it = this.probes.iterator(); it.hasNext(); ) {
                Probe p = it.next();
                if (p.step()) {
                    this.verdicts.add(p.verdict);
                    LOG.info("[PARACHUTE SELF-TEST] {}", p.verdict);
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            fail("{}", e.getMessage());
            return;
        }
        if (this.probes.isEmpty()) {
            finish(true, "id 8, registration and recipe, refusals, NBT, hurt; {}", String.join("; ", this.verdicts));
        }
    }

    @Override
    protected void cleanup() {
        for (ServerPlayer p : this.players) {
            if (p.isPassenger()) {
                p.stopRiding();
            }
        }
    }

    // ---- P0, P1 ----

    private void testIds() {
        int id = -1;
        try {
            java.lang.reflect.Field f = MchParachute.class.getDeclaredField("DATA_TYPE");
            f.setAccessible(true);
            id = ((EntityDataAccessor<?>) f.get(null)).id();
        } catch (ReflectiveOperationException e) {
            req(false, "P0: MchParachute.DATA_TYPE is readable (" + e + ")");
        }
        req(id == 8, "P0: MchParachute.DATA_TYPE is synced-data id 8, got " + id);
    }

    private void testRegistration() {
        EntityType<MchParachute> t = MchRegistries.PARACHUTE.get();
        req(t.getWidth() == 1.5F && t.getHeight() == 0.6F, "P1: mcheli:parachute is 1.5 x 0.6, got " + t.getWidth()
            + " x " + t.getHeight());
        req(t.updateInterval() == 3 && t.clientTrackingRange() == 13 && t.trackDeltas(),
            "P1: tracked to 13 chunks every 3 ticks with velocity, got range " + t.clientTrackingRange() + " interval "
                + t.updateInterval() + " deltas " + t.trackDeltas());
        req(t.canSerialize() && t.canSummon(), "P1: the parachute is saved and summonable");
        req(new ItemStack(MchRegistries.PARACHUTE_ITEM.get()).getMaxStackSize() == 1, "P1: the parachute item stacks to 1");

        Optional<RecipeHolder<?>> holder = this.server.getRecipeManager()
            .byKey(ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "parachute"));
        req(holder.isPresent() && holder.get().value() instanceof ShapedRecipe, "P1: the recipe mcheli:parachute is shaped");
        ShapedRecipe r = (ShapedRecipe) holder.get().value();
        ItemStack out = r.getResultItem(this.server.registryAccess());
        req(r.getType() == RecipeType.CRAFTING && out.is(MchRegistries.PARACHUTE_ITEM.get()) && out.getCount() == 1,
            "P1: a crafting recipe making 1 parachute, got " + out);
        req(r.getWidth() == 3 && r.getHeight() == 3, "P1: a 3x3 pattern");
        List<Ingredient> ing = r.getIngredients();
        ItemStack wool = new ItemStack(Items.WHITE_WOOL);
        ItemStack string = new ItemStack(Items.STRING);
        boolean pattern = ing.size() == 9;
        for (int i = 0; pattern && i < 9; i++) {
            Ingredient g = ing.get(i);
            boolean w = i <= 2 || i == 7;
            boolean s = i == 3 || i == 5;
            pattern = w ? g.test(wool) && !g.test(new ItemStack(Items.RED_WOOL)) : s ? g.test(string) : g.isEmpty();
        }
        req(pattern, "P1: the pattern is WWW / S S / _W_ with white wool and string");
    }

    // ---- P3, P9, P10 hurt ----

    private void testRefusals() {
        ServerPlayer ground = player("p3ground", at(-30.5, 0.0, -20.5));
        ground.setOnGround(true);
        int before = spawned().size();
        req(use(ground).isEmpty() && parachutesSince(before).isEmpty(),
            "P3: on the ground the item is spent and nothing opens");

        Pig pig = pig(at(-30.5, 0.0, -10.5), 0.0F);
        ServerPlayer rider = player("p3ride", at(-30.5, 0.0, -10.5));
        req(rider.startRiding(pig, true) && rider.isPassenger(), "P3: the test player rides a pig");
        rider.setOnGround(false);
        before = spawned().size();
        req(use(rider).isEmpty() && parachutesSince(before).isEmpty(), "P3: riding, the item is spent and nothing opens");
        rider.stopRiding();

        ServerPlayer creative = player("p3creative", at(-30.5, 20.0, -40.5));
        creative.getAbilities().instabuild = true;
        creative.setOnGround(false);
        before = spawned().size();
        ItemStack kept = use(creative);
        List<MchParachute> opened = parachutesSince(before);
        req(kept.getCount() == 1 && kept.is(MchRegistries.PARACHUTE_ITEM.get()) && opened.size() == 1,
            "P3: in creative the item is kept and the parachute opens, got " + kept + " and " + opened.size());
        opened.get(0).discard();
    }

    private void testNbt() {
        MchParachute c = new MchParachute(MchRegistries.PARACHUTE.get(), this.level);
        c.setParachuteType(ParachuteFlight.TYPE_SEAT);
        Vec3 p = at(0.5, 30.0, 0.5);
        c.setPos(p.x, p.y, p.z);
        CompoundTag tag = new CompoundTag();
        req(c.save(tag), "P9: a parachute saves");
        req("mcheli:parachute".equals(tag.getString("id")) && tag.getByte("ParachuteModelType") == 2,
            "P9: it saves as mcheli:parachute with ParachuteModelType 2, got " + tag);
        Optional<Entity> loaded = EntityType.create(tag, this.level);
        req(loaded.isPresent() && loaded.get() instanceof MchParachute, "P9: the tag loads back as a parachute");
        MchParachute l = (MchParachute) loaded.get();
        req(l.parachuteType() == 2 && l.speedMultiplier() == ParachuteFlight.MULT_MIN,
            "P9: the type round-trips and the steering multiplier starts over, got " + l.parachuteType() + " / "
                + l.speedMultiplier());
        req(!c.hurt(this.level.damageSources().generic(), 5.0F) && c.isPickable() && c.canBeCollidedWith()
                && c.isPushable(),
            "P10: a parachute cannot be hurt and is pickable, solid and pushable");
        // P11: the minecart's pickup is an unforced startRiding; the parachute refuses it, a forced mount still works.
        Minecart cart = new Minecart(this.level, p.x, p.y, p.z);
        req(!c.startRiding(cart) && !c.isPassenger(), "P11: a parachute refuses an unforced mount (a minecart's pickup)");
        req(c.startRiding(cart, true) && c.getVehicle() == cart, "P11: a forced mount (/ride, a save) still works");
        c.stopRiding();
    }

    // ---- the flights ----

    /** One flight, stepped once per test tick until it has a verdict. */
    private abstract class Probe {
        int t;
        String verdict = "";

        final boolean step() {
            this.t++;
            return run();
        }

        /** One tick (after the level ticked); true when done. */
        abstract boolean run();
    }

    /** P2. */
    private final class AirborneUse extends Probe {
        final ServerPlayer p;
        final MchParachute chute;

        AirborneUse() {
            this.p = player("p2", at(-30.5, 20.0, -30.5));
            this.p.setOnGround(false);
            this.p.fallDistance = 7.0F;
            this.p.setYRot(37.0F);
            this.p.yRotO = 37.0F;
            req(!this.p.hasInfiniteMaterials(), "P2: the test player is not in creative");
            int before = spawned().size();
            Vec3 feet = this.p.position();
            req(use(this.p).isEmpty(), "P2: an airborne use spends the parachute");
            List<MchParachute> opened = parachutesSince(before);
            req(opened.size() == 1, "P2: exactly one parachute opens, got " + opened.size());
            this.chute = opened.get(0);
            Vec3 expect = feet.add(0.5, 3.5, 0.5);
            req(this.chute.parachuteType() == ParachuteFlight.TYPE_HAND && this.chute.position().distanceTo(expect) < 1e-6,
                "P2: a type-1 parachute with its box bottom at " + expect + ", got type " + this.chute.parachuteType()
                    + " at " + this.chute.position());
            req(this.chute.fallDistance == 7.0F && this.chute.getYRot() == 37.0F && this.p.fallDistance == 0.0F,
                "P2: it takes the yaw and the fall distance, which the player loses; got fd " + this.chute.fallDistance
                    + " yaw " + this.chute.getYRot() + " player fd " + this.p.fallDistance);
        }

        @Override
        boolean run() {
            req(this.p.getVehicle() == this.chute, "P2: on the next tick the player rides the parachute");
            double yaw = this.chute.getYRot() * Math.PI / 180.0;
            Vec3 feet = new Vec3(this.chute.getX() - Math.sin(yaw) * 0.1, this.chute.getY() - 0.5,
                this.chute.getZ() + Math.cos(yaw) * 0.1);
            req(this.p.position().distanceTo(feet) < 1e-3, "P2: the rider's feet at " + feet + ", got " + this.p.position());
            this.verdict = "P2 airborne use (lift " + String.format(java.util.Locale.ROOT, "%.2f",
                this.p.getY() - 20.0 - origin.getY()) + ", rides on tick 1)";
            return true;
        }
    }

    /** P4. */
    private final class Descent extends Probe {
        final Pig pig;
        final MchParachute chute;
        double prevY;
        int steady;
        int groundAt = -1;
        int removedAt = -1;

        Descent() {
            Vec3 p = at(-10.5, 60.0, -30.5);
            this.pig = pig(p, 0.0F);
            this.chute = MchParachute.deploy(level, p.x, p.y, p.z, ParachuteFlight.TYPE_DROP, this.pig, 0.0F,
                Vec3.ZERO, 0.0F);
            this.prevY = p.y;
        }

        @Override
        boolean run() {
            if (this.t == 2) {
                req(this.pig.getVehicle() == this.chute, "P4: the pig rides its parachute");
            }
            if (this.removedAt < 0 && this.chute.isRemoved()) {
                this.removedAt = this.t;
                req(this.groundAt > 0 && Math.abs(this.removedAt - this.groundAt - 6) <= 1,
                    "P4: removed on its 6th tick starting on the ground: on the ground at " + this.groundAt
                        + ", removed at " + this.removedAt);
                req(!this.pig.isPassenger(), "P4: the pig is let off");
            }
            if (this.removedAt > 0) {
                if (this.t < this.removedAt + 10) {
                    return false;
                }
                req(this.pig.isAlive() && this.pig.getHealth() == this.pig.getMaxHealth() && !this.pig.isPassenger(),
                    "P4: the pig is free, alive and unhurt, health " + this.pig.getHealth());
                this.verdict = "P4 descent (" + this.steady + " steady ticks, ground at " + this.groundAt + ", removed "
                    + (this.removedAt - this.groundAt) + " later)";
                return true;
            }
            double y = this.chute.getY();
            if (this.groundAt < 0 && this.chute.onGround()) {
                this.groundAt = this.t;
            }
            if (this.chute.tickCount >= 100 && this.groundAt < 0) {
                double dy = y - this.prevY;
                double my = this.chute.getDeltaMovement().y;
                req(Math.abs(dy + 0.300) <= 0.005 && Math.abs(my + 0.270) <= 0.003 && this.chute.fallDistance >= 1.9F
                        && this.chute.fallDistance <= 2.1F,
                    "P4: at the steady descent dy " + dy + " mY " + my + " fall distance " + this.chute.fallDistance);
                this.steady++;
            }
            this.prevY = y;
            req(this.t < 400, "P4: the drop lands within 400 ticks");
            return false;
        }
    }

    /** P5. */
    private final class SeatLaunch extends Probe {
        final MchParachute chute;
        final double y0;
        double apex = Double.NEGATIVE_INFINITY;
        int apexAt;
        boolean open34;
        boolean open36;

        SeatLaunch() {
            Vec3 p = at(10.5, 20.0, -30.5);
            this.y0 = p.y;
            this.chute = MchParachute.deploy(level, p.x, p.y, p.z, ParachuteFlight.TYPE_SEAT, null, 0.0F,
                new Vec3(0.0, 2.0, 0.0), 0.0F);
        }

        @Override
        boolean run() {
            int age = this.chute.tickCount;
            double h = this.chute.getY() - this.y0;
            if (h > this.apex) {
                this.apex = h;
                this.apexAt = age;
            }
            if (age == 34) {
                this.open34 = this.chute.isOpenParachute();
            }
            if (age == 36) {
                this.open36 = this.chute.isOpenParachute();
            }
            if (age < 40) {
                return false;
            }
            req(Math.abs(this.apex - 26.4) <= 0.4 && Math.abs(this.apexAt - 30) <= 1,
                "P5: the seat peaks 26.4 up at tick 30, got " + this.apex + " at " + this.apexAt);
            req(!this.open34 && this.open36, "P5: closed at tick 34, open at 36, got " + this.open34 + " / " + this.open36);
            this.verdict = "P5 seat launch (apex " + String.format(java.util.Locale.ROOT, "%.3f", this.apex) + " at "
                + this.apexAt + ")";
            return true;
        }
    }

    /** P6 and the first half of P7. */
    private final class Steering extends Probe {
        final ServerPlayer p;
        final MchParachute chute;
        int phase;
        int phaseStart;
        double lastSpeed;
        double lastMult;
        float lastYaw;
        double lastY;
        String at60 = "";
        int decayTicks;

        Steering() {
            Vec3 at = at(40.5, 150.0, 30.5);
            this.p = player("p6", at);
            this.p.setYRot(90.0F);
            this.p.yRotO = 90.0F;
            this.p.zza = 1.0F;
            this.chute = MchParachute.deploy(level, at.x, at.y, at.z, ParachuteFlight.TYPE_HAND, this.p, 90.0F,
                Vec3.ZERO, 0.0F);
        }

        double speed() {
            Vec3 m = this.chute.getDeltaMovement();
            return Math.sqrt(m.x * m.x + m.z * m.z);
        }

        @Override
        boolean run() {
            req(!this.chute.isRemoved() && !this.chute.onGround(), "P6: the steered parachute is still in the air");
            switch (this.phase) {
                case 0 -> { // W for 60 ticks
                    req(this.p.getVehicle() == this.chute, "P6: the player rides");
                    if (this.t < 61) {
                        return false;
                    }
                    Vec3 m = this.chute.getDeltaMovement();
                    double s = speed();
                    double dot = -m.x / s;
                    double mult = this.chute.speedMultiplier();
                    req(dot > 0.99 && s > 0.1 && s <= 0.35, "P6: after 60 ticks of W at yaw 90 it moves toward -X at "
                        + "0.1 to 0.35, got " + m);
                    req(mult > ParachuteFlight.MULT_MIN && mult <= ParachuteFlight.MULT_MAX,
                        "P6: the multiplier ramped, got " + mult);
                    req(this.chute.getYRot() == this.p.getYRot() % 360.0F, "P6: the parachute takes the rider's yaw, got "
                        + this.chute.getYRot());
                    this.at60 = String.format(java.util.Locale.ROOT, "speed %.4f mult %.4f", s, mult);
                    this.p.zza = 0.0F;
                    this.phase = 1;
                    this.phaseStart = this.t;
                }
                case 1 -> { // no input: the multiplier decays to 0.07
                    if (Math.abs(this.chute.speedMultiplier() - ParachuteFlight.MULT_MIN) > 1e-3) {
                        req(this.t - this.phaseStart < 250, "P6: with no input the multiplier decays to 0.07, still "
                            + this.chute.speedMultiplier());
                        return false;
                    }
                    this.decayTicks = this.t - this.phaseStart;
                    this.p.zza = -1.0F;
                    this.phase = 2;
                    this.phaseStart = this.t;
                    this.lastSpeed = speed();
                    this.lastMult = this.chute.speedMultiplier();
                }
                case 2 -> { // S for 10 ticks adds nothing
                    double s = speed();
                    double mult = this.chute.speedMultiplier();
                    req(s <= this.lastSpeed * 0.95 + 1e-9 && mult <= this.lastMult,
                        "P6: S pushes nothing (speed " + this.lastSpeed + " -> " + s + ", mult " + this.lastMult + " -> "
                            + mult + ")");
                    this.lastSpeed = s;
                    this.lastMult = mult;
                    if (this.t - this.phaseStart < 10) {
                        return false;
                    }
                    this.p.zza = 0.0F;
                    this.p.setShiftKeyDown(true);
                    this.phase = 3;
                    this.phaseStart = this.t;
                }
                case 3 -> { // P7: sneaking drops the player
                    req(!this.p.isPassenger() && !this.chute.isVehicle(), "P7: sneaking drops the player");
                    this.lastYaw = this.chute.getYRot();
                    this.lastY = this.chute.getY();
                    this.phase = 4;
                    this.phaseStart = this.t;
                }
                default -> { // riderless, it falls on and turns at most 20 a tick
                    float yaw = this.chute.getYRot();
                    req(Math.abs(Mth.wrapDegrees(yaw - this.lastYaw)) <= 20.0F + 1e-4F && this.chute.getY() < this.lastY,
                        "P7: riderless it falls on and turns at most 20 a tick (" + this.lastYaw + " -> " + yaw + ")");
                    this.lastYaw = yaw;
                    this.lastY = this.chute.getY();
                    if (this.t - this.phaseStart < 10) {
                        return false;
                    }
                    this.verdict = "P6 steering (" + this.at60 + ", decayed in " + this.decayTicks
                        + " ticks), P7 sneak drop";
                    return true;
                }
            }
            return false;
        }
    }

    /** P7, the fixed riderless heading. */
    private final class RiderlessHeading extends Probe {
        final MchParachute chute;
        final List<Float> yaws = new ArrayList<>();

        RiderlessHeading() {
            Vec3 p = at(-40.5, 30.0, 10.5);
            this.chute = MchParachute.deploy(level, p.x, p.y, p.z, ParachuteFlight.TYPE_DROP, null, 90.0F,
                new Vec3(0.0, 0.0, 0.3), 0.0F);
        }

        @Override
        boolean run() {
            this.yaws.add(this.chute.getYRot());
            if (this.yaws.size() < 6) {
                return false;
            }
            float[] expect = {70.0F, 50.0F, 30.0F, 10.0F, 0.0F, 0.0F};
            for (int i = 0; i < expect.length; i++) {
                req(Math.abs(this.yaws.get(i) - expect[i]) < 1e-3F, "P7: drifting +Z from yaw 90 it turns to 0 at 20 a "
                    + "tick (the reference turned toward 180), got " + this.yaws);
            }
            this.verdict = "P7 riderless heading " + this.yaws;
            return true;
        }
    }

    /** P8: a pool of {@code (2 half + 1)^2 x 3} water cells dug into the ground at {@code (dx, dz)}. */
    private final class WaterLanding extends Probe {
        final String name;
        final boolean expectBoat;
        final int px;
        final int pz;
        final Pig pig;
        final MchParachute chute;

        WaterLanding(String name, int dx, int dz, int half, int type, boolean expectBoat) {
            this.name = name;
            this.expectBoat = expectBoat;
            this.px = origin.getX() + dx;
            this.pz = origin.getZ() + dz;
            int top = origin.getY() - 1; // the ground's top block
            for (int y = 0; y < 3; y++) {
                for (int x = -half; x <= half; x++) {
                    for (int z = -half; z <= half; z++) {
                        level.setBlock(new BlockPos(this.px + x, top - y, this.pz + z), Blocks.WATER.defaultBlockState(), 3);
                    }
                }
            }
            // An integer x/z: the probe cell floor(x + 0.5) is then this very cell, the pool's centre.
            Vec3 p = new Vec3(this.px, origin.getY() + 4.0, this.pz);
            this.pig = pig(p, 30.0F);
            this.chute = MchParachute.deploy(level, p.x, p.y, p.z, type, this.pig, 30.0F, Vec3.ZERO, 0.0F);
        }

        @Override
        boolean run() {
            if (!this.chute.isRemoved()) {
                req(this.t < 120, "P8 " + this.name + ": the parachute touches the water within 120 ticks");
                return false;
            }
            List<Boat> boats = new ArrayList<>();
            for (Entity e : spawned()) {
                if (e instanceof Boat b && !b.isRemoved() && Math.abs(b.getX() - this.px) < 3.0
                        && Math.abs(b.getZ() - this.pz) < 3.0) {
                    boats.add(b);
                }
            }
            if (this.expectBoat) {
                req(boats.size() == 1, "P8 " + this.name + ": one boat, got " + boats.size());
                Boat b = boats.get(0);
                Vec3 expect = new Vec3(this.px, origin.getY(), this.pz);
                req(b.position().distanceTo(expect) < 1e-6, "P8 " + this.name + ": the boat at " + expect + ", got "
                    + b.position());
                req(b.getYRot() == this.chute.getYRot() && this.pig.getVehicle() == b,
                    "P8 " + this.name + ": the boat faces the parachute's heading (" + this.chute.getYRot() + ", got "
                        + b.getYRot() + ") and carries the pig");
            } else {
                req(boats.isEmpty() && !this.pig.isPassenger(), "P8 " + this.name + ": no boat, the pig let off, got "
                    + boats.size() + " boats");
            }
            this.verdict = "P8 water " + this.name + " (" + (this.expectBoat ? "boat" : "no boat") + " at tick " + this.t
                + ")";
            return true;
        }
    }

    /** P10, the rider's removal. */
    private final class RiderDeath extends Probe {
        final Pig pig;
        final MchParachute chute;
        int killedAt = -1;
        int pigGoneAt = -1;

        RiderDeath() {
            Vec3 p = at(0.5, 80.0, 30.5);
            this.pig = pig(p, 0.0F);
            this.chute = MchParachute.deploy(level, p.x, p.y, p.z, ParachuteFlight.TYPE_DROP, this.pig, 0.0F,
                Vec3.ZERO, 0.0F);
        }

        @Override
        boolean run() {
            if (this.t == 3) {
                req(this.pig.getVehicle() == this.chute, "P10: the pig rides");
                this.pig.kill();
                this.killedAt = this.t;
                return false;
            }
            if (this.killedAt < 0) {
                return false;
            }
            if (this.pigGoneAt < 0) {
                if (this.pig.isRemoved()) {
                    this.pigGoneAt = this.t;
                    req(this.pigGoneAt - this.killedAt >= 15, "P10: the pig was removed only after its death, at "
                        + (this.pigGoneAt - this.killedAt));
                    return false;
                }
                req(!this.chute.isRemoved() && this.pig.getVehicle() == this.chute,
                    "P10: the dying pig stays aboard and the parachute keeps it (as in 1.7.10)");
                req(this.t - this.killedAt < 40, "P10: the killed pig is removed within 40 ticks");
                return false;
            }
            if (!this.chute.isRemoved()) {
                req(this.t - this.pigGoneAt < 2, "P10: the parachute goes within 2 ticks of its rider");
                return false;
            }
            this.verdict = "P10 rider removed (parachute gone " + (this.t - this.pigGoneAt) + " tick(s) after the pig, "
                + (this.pigGoneAt - this.killedAt) + " after the kill)";
            return true;
        }
    }

    /** P10, the push. */
    private final class PushApart extends Probe {
        final MchParachute a;
        final MchParachute b;

        PushApart() {
            Vec3 p = at(-20.5, 40.0, 30.5);
            this.a = MchParachute.deploy(level, p.x, p.y, p.z, ParachuteFlight.TYPE_DROP, null, 0.0F, Vec3.ZERO, 0.0F);
            this.b = MchParachute.deploy(level, p.x + 0.1, p.y, p.z, ParachuteFlight.TYPE_DROP, null, 0.0F, Vec3.ZERO,
                0.0F);
        }

        @Override
        boolean run() {
            if (this.t < 10) {
                return false;
            }
            double dx = this.b.getX() - this.a.getX();
            req(dx > 1.5, "P10: two overlapping parachutes push apart past their boxes within 10 ticks, got " + dx);
            this.verdict = "P10 push apart (" + String.format(java.util.Locale.ROOT, "%.3f", dx) + " in 10 ticks)";
            return true;
        }
    }

    /**
     * P11: a minecart running +Z down a rail at 0.3 a tick past a parachute standing beside the rail with a pig: the
     * parachute's box 0.1 clear of the cart's (so the cart brushes it, inside its 0.2 pickup margin, rather than
     * bumping into it) and level with it. The 1.21 cart mounts every pushable non-player entity in that margin while
     * it runs faster than 0.1 a tick (AbstractMinecart.tick); the parachute must never ride it.
     */
    private final class CartPass extends Probe {
        final Minecart cart;
        final MchParachute chute;
        final Pig pig;
        int brushed;

        CartPass() {
            int bx = origin.getX() + 40;
            int by = origin.getY() + 30;
            int bz = origin.getZ() - 36;
            for (int z = -5; z <= 5; z++) {
                for (int x = -1; x <= 3; x++) {
                    level.setBlock(new BlockPos(bx + x, by, bz + z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            for (int z = -5; z <= 5; z++) {
                level.setBlock(new BlockPos(bx, by + 1, bz + z), Blocks.RAIL.defaultBlockState(), 3); // north-south
            }
            this.cart = new Minecart(level, bx + 0.5, by + 1.0, bz - 0.5);
            this.cart.setDeltaMovement(0.0, 0.0, 0.3);
            level.addFreshEntity(this.cart);
            double half = EntityType.MINECART.getWidth() / 2.0 + 0.1 + MchRegistries.PARACHUTE.get().getWidth() / 2.0;
            Vec3 p = new Vec3(bx + 0.5 + half, by + 1.0, bz + 0.5);
            this.pig = pig(new Vec3(p.x + 1.5, p.y, p.z), 0.0F); // clear of the cart; it mounts on the first tick
            this.chute = MchParachute.deploy(level, p.x, p.y, p.z, ParachuteFlight.TYPE_DROP, this.pig, 0.0F, Vec3.ZERO,
                0.0F);
        }

        @Override
        boolean run() {
            req(!(this.chute.getVehicle() instanceof Minecart) && !(this.cart.getFirstPassenger() instanceof MchParachute),
                "P11: the minecart took the parachute at tick " + this.t);
            if (!this.chute.isRemoved()) {
                if (this.t == 2) {
                    req(this.pig.getVehicle() == this.chute, "P11: the pig rides the parachute");
                }
                boolean fast = this.cart.getDeltaMovement().horizontalDistanceSqr() > 0.01;
                boolean margin = this.cart.getBoundingBox().inflate(0.2, 0.0, 0.2).intersects(this.chute.getBoundingBox());
                if (fast && margin) {
                    this.brushed++;
                }
                req(this.t < 40, "P11: the parachute lands within 40 ticks");
                return false;
            }
            req(this.brushed >= 3, "P11: the running cart had the parachute in its pickup margin for at least 3 ticks, "
                + "got " + this.brushed);
            req(!(this.pig.getVehicle() instanceof MchParachute),
                "P11: the pig is let off as the parachute lands");
            this.verdict = "P11 minecart pass (in the cart's pickup margin " + this.brushed + " ticks, never taken, "
                + "landed at tick " + this.t + ")";
            return true;
        }
    }

    // ---- helpers ----

    private Vec3 at(double dx, double dy, double dz) {
        return new Vec3(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
    }

    private ServerPlayer player(String label, Vec3 at) {
        ServerPlayer p = newTestPlayer(label);
        p.setPos(at.x, at.y, at.z);
        this.players.add(p);
        return p;
    }

    private Pig pig(Vec3 at, float yaw) {
        Pig pig = EntityType.PIG.create(this.level);
        req(pig != null, "a pig can be created");
        pig.setNoAi(true);
        pig.setPos(at.x, at.y, at.z);
        pig.setYRot(yaw);
        pig.yRotO = yaw;
        this.level.addFreshEntity(pig);
        return pig;
    }

    /** {@code p} uses a parachute held in the main hand; returns the stack left in that hand. */
    private ItemStack use(ServerPlayer p) {
        p.setItemInHand(HAND, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        p.getItemInHand(HAND).use(this.level, p, HAND);
        return p.getItemInHand(HAND);
    }

    private List<MchParachute> parachutesSince(int index) {
        List<MchParachute> out = new ArrayList<>();
        List<Entity> all = spawned();
        for (int i = index; i < all.size(); i++) {
            if (all.get(i) instanceof MchParachute c) {
                out.add(c);
            }
        }
        return out;
    }

    private static void req(boolean cond, String what) {
        if (!cond) {
            throw new IllegalStateException(what);
        }
    }
}
