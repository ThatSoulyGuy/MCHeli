package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.agnostic.weapon.SeatWeaponSync;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The synced weapon view of one vehicle, on both sides.
 * <ul>
 *   <li>SERVER publishing: every seat's selection and every weapon's mode
 *       ({@link AbstractMchVehicle#DATA_SEAT_WEAPONS}), the weapon totals ({@link AbstractMchVehicle#DATA_AMMO_ALL}),
 *       the spent-rail mask
 *       ({@link AbstractMchVehicle#DATA_WEAPON_SPENT}), and each riding player's own seat-weapon notice
 *       ({@link mcheli.dependent.control.ClientboundSeatWeaponPayload}). The pure derivations live in
 *       {@link SeatWeaponSync}.</li>
 *   <li>The readers, for the HUD and the client and for server diagnostics: selection, mode, name with its mode
 *       suffix, magazine, reload, heat, preparation, sight type, rest ammo, reload seconds, the missile-part hiding,
 *       the would-switch test, and the config-resolved weapon list ({@code weaponCount}, {@code weaponAt},
 *       {@code ammoOf}, {@code hudName}).</li>
 * </ul>
 * It is the only writer of DATA_SEAT_WEAPONS, DATA_AMMO_ALL and DATA_WEAPON_SPENT. The entity keeps every reader as a
 * public one-line delegate, and the package bridges {@code publishSeatWeapons()} and {@code selectedHudSlot(int)}.
 */
final class MchWeaponSync {
    private final AbstractMchVehicle v;

    MchWeaponSync(AbstractMchVehicle v) {
        this.v = v;
    }

    /** A config change: the client weapon list rebuilds against the new config on its next read. */
    void onConfigChanged() {
        this.hudWeapons = null;
    }

    /** Index of {@code seat}'s selected weapon (synced, {@link AbstractMchVehicle#DATA_SEAT_WEAPONS}), or -1 when that
     *  seat has none. */
    int selectedWeaponIndex(int seat) {
        int[] s = v.getEntityData().get(AbstractMchVehicle.DATA_SEAT_WEAPONS).getIntArray("s");
        return seat >= 0 && seat < s.length ? s[seat] : -1;
    }

    /** A seat's quick-changing weapon numbers: rounds in the magazine (-1 endless), reload/cooldown 0..1, barrel heat
     *  0..1, in preparation (reloading or inside a switch lockout). */
    private record SeatWeaponHud(int ammo, float reload, float heat, boolean inPreparation) {}

    /** CLIENT: the {@link SeatWeaponHud} this client's rider last received for each seat (only his own seat is ever
     *  sent, {@link mcheli.dependent.control.ClientboundSeatWeaponPayload}). */
    private final java.util.Map<Integer, SeatWeaponHud> seatWeaponHud = new java.util.HashMap<>();

    /** CLIENT: store {@code seat}'s weapon numbers from the server (the seat-weapon payload handler). */
    void acceptSeatWeaponHud(int seat, int ammo, float reload, float heat, boolean inPreparation) {
        if (v.level().isClientSide) {
            this.seatWeaponHud.put(seat, new SeatWeaponHud(ammo, reload, heat, inPreparation));
        }
    }

    /** {@code seat}'s weapon numbers: on the client as its rider last received them, on the server live from the
     *  loadout (diagnostics, self-tests). Null without a selected weapon or before the first notice. */
    private SeatWeaponHud seatWeaponHud(int seat) {
        if (v.level().isClientSide) {
            return this.seatWeaponHud.get(seat);
        }
        WeaponSlot s = v.weaponLoadout() != null ? v.weaponLoadout().selected(seat) : null;
        return s != null ? new SeatWeaponHud(s.magazine(), s.reloadFraction(), s.heatFraction(), s.inPreparation())
            : null;
    }

    /** The current MODE of weapon {@code index} (synced; 0 when the weapon has only one). */
    int weaponMode(int index) {
        int[] m = v.getEntityData().get(AbstractMchVehicle.DATA_SEAT_WEAPONS).getIntArray("m");
        return index >= 0 && index < m.length ? m[index] : 0;
    }

    // Client-side lazy weapon list, used only to resolve a synced selected index to its config (name, sight, ...).
    private VehicleWeapons hudWeapons;

    /** The client-side weapon list (config-resolved, ammo lives in the synced fields), built lazily; null if no info. */
    private VehicleWeapons clientWeapons() {
        if (v.weaponHostInfo() == null) {
            return null;
        }
        if (this.hudWeapons == null) {
            this.hudWeapons = VehicleWeapons.build(v.weaponHostInfo(), MCH_WeaponInfoManager::get, v.seatCount());
        }
        return this.hudWeapons;
    }

    /** {@code seat}'s selected weapon slot (from the synced index), for the HUD; null if none. */
    WeaponSlot selectedHudSlot(int seat) {
        VehicleWeapons vw = clientWeapons();
        int idx = v.selectedWeaponIndex(seat);
        return vw != null && idx >= 0 && idx < vw.size() ? vw.get(idx) : null;
    }

    /** CLIENT: would a weapon switch of {@code seat} by {@code step} land on a DIFFERENT weapon? The reference cancels a
     *  mouse-wheel turn (it does not scroll the hotbar) only then ({@code MCH_ClientTickHandlerBase.updateMouseWheel}). */
    boolean wouldSwitchWeapon(int seat, int step) {
        VehicleWeapons vw = clientWeapons();
        int cur = v.selectedWeaponIndex(seat);
        if (vw == null || cur < 0) {
            return false;
        }
        vw.setSelectedIndex(seat, cur); // mirror the synced selection before asking where a switch would go
        int next = vw.nextEligible(seat, step, s -> v.seatPassenger(s) instanceof Player);
        return next >= 0 && next != cur;
    }

    /** True if the MISSILE part for the weapon {@code weaponName} should be hidden this frame — its round is spent and
     *  reloading (synced {@link AbstractMchVehicle#DATA_WEAPON_SPENT}), so the fired rail reads as empty (reference
     *  renderWeapon gate). */
    boolean isMissilePartHidden(String weaponName) {
        if (weaponName == null) {
            return false;
        }
        int mask = v.getEntityData().get(AbstractMchVehicle.DATA_WEAPON_SPENT);
        if (mask == 0) {
            return false;
        }
        VehicleWeapons vw = clientWeapons();
        if (vw == null) {
            return false;
        }
        for (int i = 0; i < vw.size() && i < 32; i++) {
            if (weaponName.equalsIgnoreCase(vw.get(i).weaponName)) {
                return (mask & 1 << i) != 0;
            }
        }
        return false;
    }

    /** {@code seat}'s selected weapon name for the HUD with its mode suffix ({@code " [HE]"}, {@code " [TA]"}, ...;
     *  reference {@code MCH_WeaponSet.getName}); "" if none. */
    String selectedWeaponName(int seat) {
        WeaponSlot slot = v.selectedHudSlot(seat);
        if (slot == null) {
            return "";
        }
        String base = slot.info.displayName != null && !slot.info.displayName.isEmpty()
            ? slot.info.displayName : slot.weaponName;
        return base + mcheli.agnostic.weapon.MCH_WeaponMode.suffix(slot.info.type, v.weaponMode(v.selectedWeaponIndex(seat)));
    }

    /** Rounds in {@code seat}'s selected magazine (sent to its rider); -1 == no weapon or an endless magazine. */
    int selectedAmmo(int seat) {
        SeatWeaponHud s = seatWeaponHud(seat);
        return s != null ? s.ammo() : -1;
    }

    /** True if {@code seat}'s selected weapon is a heat/IR-seeker (config {@code MaxHeatCount &gt; 0}) — the HUD's
     *  {@code is_heat_wpn} lock-cue gate (reference {@code MCH_HudItem}: {@code is_heat_wpn = wi.maxHeatCount &gt; 0}). */
    boolean isSelectedWeaponHeat(int seat) {
        WeaponSlot slot = v.selectedHudSlot(seat);
        return slot != null && slot.info.maxHeatCount > 0;
    }

    /** {@code seat}'s aiming-reticle type for the HUD {@code sight_type} overlay (reference
     *  {@code MCH_HudItem.updateVarMap_Weapon}): 2 for a lock-on sight ({@code sight = missilesight}) on a weapon that
     *  locks (the reference also requires {@code getLockCountMax() > 0}, which only its AA/AT seeker weapons have), 1 for
     *  a ballistic move-sight ({@code sight = movesight}), otherwise 0. */
    int selectedWeaponSightType(int seat) {
        WeaponSlot slot = v.selectedHudSlot(seat);
        if (slot == null) {
            return 0;
        }
        return switch (slot.info.sight) {
            case LOCK -> AbstractMchVehicle.isLockOnType(slot.info.type) ? 2 : 0;
            case ROCKET -> 1;
            default -> 0;
        };
    }

    /** CLIENT: {@code seat}'s selected weapon config (for the lock-on tracker), or null if no weapon is selected. */
    MCH_WeaponInfo selectedClientWeaponInfo(int seat) {
        WeaponSlot slot = v.selectedHudSlot(seat);
        return slot != null ? slot.info : null;
    }

    /** {@code seat}'s selected weapon's {@code DisplayMortarDistance} flag — gates the HUD {@code mortar} readout. */
    boolean selectedWeaponDisplaysMortarDist(int seat) {
        WeaponSlot slot = v.selectedHudSlot(seat);
        return slot != null && slot.info.displayMortarDistance;
    }

    /** {@code seat}'s reload/cooldown progress 0..1 (sent to its rider), for the HUD {@code reload_time} bar;
     *  0 == ready. */
    float selectedReload(int seat) {
        SeatWeaponHud s = seatWeaponHud(seat);
        return s != null ? s.reload() : 0.0F;
    }

    /** {@code seat}'s barrel heat 0..1 (sent to its rider), for the HUD {@code wpn_heat} overheat gauge;
     *  1 == overheated. */
    float selectedHeat(int seat) {
        SeatWeaponHud s = seatWeaponHud(seat);
        return s != null ? s.heat() : 0.0F;
    }

    /** Reference {@code isInPreparation} of {@code seat}'s weapon (sent to its rider): reloading or inside a switch
     *  lockout — the HUD {@code reloading}. */
    boolean selectedInPreparation(int seat) {
        SeatWeaponHud s = seatWeaponHud(seat);
        return s != null && s.inPreparation();
    }

    /** {@code seat}'s remaining cooldown in SECONDS — the received fraction × the config interval (delay/reload)
     *  ÷ 20 tps — for the HUD's {@code RELOAD_SEC} readout. Computed client-side from the config interval. */
    float selectedReloadSeconds(int seat) {
        WeaponSlot slot = v.selectedHudSlot(seat);
        return slot != null ? v.selectedReload(seat) * slot.reloadIntervalTicks() / 20.0F : 0.0F;
    }

    /** The HUD config name for a seat (0 = pilot), from this vehicle's {@code HUD =} config list, or null. A {@code none}
     *  (or blank) entry means the seat has NO cockpit HUD and maps to null, so the caller keeps the vanilla crosshair
     *  rather than suppressing it for an empty overlay (the reference has no HudManager entry for {@code none}). */
    String hudName(int seat) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || info.hudList == null || seat < 0 || seat >= info.hudList.size()) {
            return null;
        }
        String name = info.hudList.get(seat);
        return name == null || name.isEmpty() || name.equalsIgnoreCase("none") ? null : name;
    }

    /** {@code seat}'s selected weapon's RESERVE (reference {@code getRestAllAmmoNum}) = its total minus the magazine in
     *  the gun. -1 when the weapon has no finite reserve. CLIENT-readable (both halves are synced). */
    int selectedRestAmmo(int seat) {
        int total = v.ammoOf(v.selectedWeaponIndex(seat));
        return total < 0 ? -1 : Math.max(0, total - Math.max(0, v.selectedAmmo(seat)));
    }

    /** SERVER: the seat selections and weapon modes last published in {@link AbstractMchVehicle#DATA_SEAT_WEAPONS}. */
    private int[] publishedSelection;
    private int[] publishedModes;
    /** SERVER: rider entity id -> the seat-weapon notice last sent to that rider ({@link #packSeatWeaponHud}). */
    private final java.util.Map<Integer, Long> seatWeaponHudSent = new java.util.HashMap<>();
    /** Ticks between unconditional re-sends of a rider's seat-weapon notice, so one that reached a client before the
     *  vehicle did (a rider logging in aboard) is made good. */
    private static final int SEAT_WEAPON_REFRESH = 40;

    /**
     * SERVER: publish every seat's selection and every weapon's mode ({@link AbstractMchVehicle#DATA_SEAT_WEAPONS},
     * rebuilt only when one of them changed), then send each riding player his own seat's magazine, reload, heat and
     * preparation when their quantised values changed ({@link mcheli.dependent.control.ClientboundSeatWeaponPayload}).
     * In steady state nothing is sent; in combat only the firing seat's rider gets a small notice per change, where the
     * old per-tick compound of every seat went to every tracking player.
     */
    void publishSelection(VehicleWeapons weapons) {
        int[] selection = SeatWeaponSync.selection(weapons, v.seatCount());
        int[] modes = SeatWeaponSync.modes(weapons);
        boolean anyMode = modes.length != 0; // SeatWeaponSync.modes is empty exactly when no mode is non-zero
        if (!java.util.Arrays.equals(selection, this.publishedSelection)
            || !java.util.Arrays.equals(modes, this.publishedModes)) {
            CompoundTag t = new CompoundTag();
            t.putIntArray("s", selection);
            if (anyMode) {
                t.putIntArray("m", modes);
            }
            v.getEntityData().set(AbstractMchVehicle.DATA_SEAT_WEAPONS, t);
            this.publishedSelection = selection;
            this.publishedModes = modes;
        }

        java.util.Set<Integer> riders = new java.util.HashSet<>();
        for (Entity p : v.getPassengers()) {
            int seat = v.seatIndexOf(p);
            WeaponSlot s = seat >= 0 ? weapons.selected(seat) : null;
            if (!(p instanceof net.minecraft.server.level.ServerPlayer sp) || s == null) {
                continue;
            }
            riders.add(sp.getId());
            long packed = packSeatWeaponHud(seat, s);
            Long last = this.seatWeaponHudSent.get(sp.getId());
            if (last == null || last != packed || v.tickCount % SEAT_WEAPON_REFRESH == 0) {
                this.seatWeaponHudSent.put(sp.getId(), packed);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new mcheli.dependent.control.ClientboundSeatWeaponPayload(v.getId(), seat, s.magazine(),
                        mcheli.dependent.control.ClientboundSeatWeaponPayload.quantise(s.reloadFraction()),
                        mcheli.dependent.control.ClientboundSeatWeaponPayload.quantise(s.heatFraction()),
                        s.inPreparation()));
            }
        }
        this.seatWeaponHudSent.keySet().retainAll(riders);
    }

    /** One rider's seat-weapon notice as a comparable key: the seat, the magazine and the quantised reload/heat bytes
     *  and preparation flag the payload carries ({@link SeatWeaponSync#packNotice}). */
    private static long packSeatWeaponHud(int seat, WeaponSlot s) {
        long reload = mcheli.dependent.control.ClientboundSeatWeaponPayload.quantise(s.reloadFraction()) & 0xFFL;
        long heat = mcheli.dependent.control.ClientboundSeatWeaponPayload.quantise(s.heatFraction()) & 0xFFL;
        return SeatWeaponSync.packNotice(seat, s.magazine(), reload, heat, s.inPreparation());
    }

    /** Publish every weapon's TOTAL ammo (magazine + reserve) for the riding GUI, and the spent-rail mask
     *  ({@link SeatWeaponSync#spentMask}). SynchedEntityData compares before it marks dirty, so an unchanged map costs
     *  no packets. */
    void publishAmmo(VehicleWeapons weapons) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < weapons.size(); i++) {
            WeaponSlot w = weapons.get(i);
            if (w.hasEconomy()) {
                t.putInt(Integer.toString(i), w.totalAmmo());
            }
        }
        int spent = SeatWeaponSync.spentMask(weapons);
        v.getEntityData().set(AbstractMchVehicle.DATA_AMMO_ALL, t);
        v.getEntityData().set(AbstractMchVehicle.DATA_WEAPON_SPENT, spent);
    }

    /** Total ammo (magazine + reserve) of weapon {@code i} — CLIENT-readable, for the riding GUI. -1 == infinite. */
    int ammoOf(int i) {
        CompoundTag t = v.getEntityData().get(AbstractMchVehicle.DATA_AMMO_ALL);
        String k = Integer.toString(i);
        return t.contains(k) ? t.getInt(k) : -1;
    }

    /** The number of weapons this vehicle has (CLIENT-readable, from the config). */
    int weaponCount() {
        return hudWeaponList() != null ? hudWeaponList().size() : 0;
    }

    /** Weapon {@code i}'s slot, resolved from the config — CLIENT-safe (ammo lives in {@link #ammoOf}). */
    WeaponSlot weaponAt(int i) {
        VehicleWeapons list = hudWeaponList();
        return list != null && i >= 0 && i < list.size() ? list.get(i) : null;
    }

    private VehicleWeapons hudWeaponList() {
        return clientWeapons();
    }
}
