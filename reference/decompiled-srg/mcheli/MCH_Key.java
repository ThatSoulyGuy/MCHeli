/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import mcheli.wrapper.W_KeyBinding;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_Key {
    public int key;
    private boolean isPress;
    private boolean isBeforePress;

    public MCH_Key(int k) {
        this.key = k;
        this.isPress = false;
        this.isBeforePress = false;
    }

    public boolean isKeyDown() {
        return !this.isBeforePress && this.isPress;
    }

    public boolean isKeyPress() {
        return this.isPress;
    }

    public boolean isKeyUp() {
        return this.isBeforePress && !this.isPress;
    }

    public void update() {
        if (this.key == 0) {
            return;
        }
        this.isBeforePress = this.isPress;
        this.isPress = this.key >= 0 ? Keyboard.isKeyDown((int)this.key) : Mouse.isButtonDown((int)(this.key + 100));
    }

    public static boolean isKeyDown(int key) {
        if (key > 0) {
            return Keyboard.isKeyDown((int)key);
        }
        if (key < 0) {
            return Mouse.isButtonDown((int)(key + 100));
        }
        return false;
    }

    public static boolean isKeyDown(KeyBinding keyBind) {
        return MCH_Key.isKeyDown((int)W_KeyBinding.getKeyCode((KeyBinding)keyBind));
    }
}

