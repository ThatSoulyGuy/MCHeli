package mcheli.agnostic.spi;

import mcheli.agnostic.value.Vec3d;

/**
 * Server→client notification channel. The agnostic layer says WHAT to notify (lock-on, missile alerts,
 * controlled-entity notices); the wire packets are rewritten natively in the dependent layer.
 */
public interface Network {
    void sendLockNotify(EntityRef target, EntityRef locker);
    void sendMissileAlert(EntityRef target, Vec3d missilePos);
    void sendControlEntity(EntityRef player, EntityRef controlled);
}
