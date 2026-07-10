package mcheli.agnostic.spi;

/**
 * Client-only singleton access that is NOT HUD drawing: local player, first-person state, render-view
 * entity, screen control, and vision effects (night-vision / thermal / zoom). Client side only.
 */
public interface ClientContext {
    EntityRef localPlayer();
    boolean isFirstPerson();
    boolean isGamePaused();

    void setRenderViewEntity(EntityRef entity);
    EntityRef renderViewEntity();
    void closeScreen();

    void setNightVision(boolean on);
    void setZoom(float factor);
}
