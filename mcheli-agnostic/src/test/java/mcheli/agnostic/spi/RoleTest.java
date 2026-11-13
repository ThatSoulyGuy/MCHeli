package mcheli.agnostic.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Locks the {@link Role} groupings. Every constant has a dependent counterpart ({@code NeoEntityRef.role()}); the
 * reference's seat and UAV categories are intentionally absent (vanilla passengers replace seat entities, and a UAV is a
 * heli/plane entity whose config sets {@code isUAV}).
 */
class RoleTest {

    private static Set<Role> where(java.util.function.Predicate<Role> p) {
        EnumSet<Role> out = EnumSet.noneOf(Role.class);
        for (Role r : Role.values()) {
            if (p.test(r)) {
                out.add(r);
            }
        }
        return out;
    }

    @Test
    void groupings() {
        assertEquals(EnumSet.of(Role.HELICOPTER, Role.PLANE, Role.TANK, Role.VEHICLE), where(Role::isMchVehicle));
        assertEquals(EnumSet.of(Role.HELICOPTER, Role.PLANE), where(Role::isAircraft));
        assertEquals(EnumSet.of(Role.PLAYER, Role.LIVING, Role.MOB), where(Role::isLivingLike));
    }

    @Test
    void supportAndOtherRolesBelongToNoGroup() {
        for (Role r : EnumSet.of(Role.HITBOX, Role.PROJECTILE, Role.OTHER)) {
            assertEquals(false, r.isMchVehicle() || r.isAircraft() || r.isLivingLike(), r.name());
        }
    }
}
