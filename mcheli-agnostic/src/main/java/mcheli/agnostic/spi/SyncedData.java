package mcheli.agnostic.spi;

/**
 * Typed accessors over an entity's synchronized data — the reference's {@code DataWatcher} slots
 * (throttle, fuel, damage, texture, command/status bitfields, fold/part state, ...). Reached via
 * {@link EntityRef#synced()}; the dependent layer maps ids to real {@code SynchedEntityData} keys.
 */
public interface SyncedData {
    int getInt(int id);        void setInt(int id, int value);
    byte getByte(int id);      void setByte(int id, byte value);
    float getFloat(int id);    void setFloat(int id, float value);
    String getString(int id);  void setString(int id, String value);

    /** Read/write a single bit of a packed int/byte status field. */
    boolean getFlag(int id, int bit);
    void setFlag(int id, int bit, boolean value);
}
