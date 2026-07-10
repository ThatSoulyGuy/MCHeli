/*
 * Decompiled with CFR 0.152.
 */
package mcheli.particles;

import mcheli.MCH_Lib;
import mcheli.multiplay.MCH_GuiTargetMarker;
import mcheli.particles.MCH_EntityParticleBase;
import mcheli.wrapper.W_Reflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class MCH_EntityParticleMarkPoint
extends MCH_EntityParticleBase {
    final Team taem;

    public MCH_EntityParticleMarkPoint(World par1World, double x, double y, double z, Team team) {
        super(par1World, x, y, z, 0.0, 0.0, 0.0);
        this.setParticleMaxAge(30);
        this.taem = team;
    }

    public void func_70071_h_() {
        this.field_70169_q = this.field_70165_t;
        this.field_70167_r = this.field_70163_u;
        this.field_70166_s = this.field_70161_v;
        EntityClientPlayerMP player = Minecraft.func_71410_x().field_71439_g;
        if (player == null) {
            this.func_70106_y();
        } else if (player.func_96124_cp() == null && this.taem != null) {
            this.func_70106_y();
        } else if (player.func_96124_cp() != null && !player.func_142012_a(this.taem)) {
            this.func_70106_y();
        }
    }

    public void func_70106_y() {
        super.func_70106_y();
        MCH_Lib.DbgLog((boolean)true, (String)("MCH_EntityParticleMarkPoint.setDead : " + this), (Object[])new Object[0]);
    }

    public int func_70537_b() {
        return 3;
    }

    public void func_70539_a(Tessellator par1Tessellator, float par2, float par3, float par4, float par5, float par6, float par7) {
        double pz;
        double py;
        double px;
        double scale;
        GL11.glPushMatrix();
        Minecraft mc = Minecraft.func_71410_x();
        EntityClientPlayerMP player = mc.field_71439_g;
        if (player == null) {
            return;
        }
        double ix = field_70556_an;
        double iy = field_70554_ao;
        double iz = field_70555_ap;
        if (mc.field_71474_y.field_74320_O > 0 && mc.field_71451_h != null) {
            EntityLivingBase viewer = mc.field_71451_h;
            double dist = W_Reflection.getThirdPersonDistance();
            float yaw = mc.field_71474_y.field_74320_O != 2 ? -viewer.field_70177_z : -viewer.field_70177_z;
            float pitch = mc.field_71474_y.field_74320_O != 2 ? -viewer.field_70125_A : -viewer.field_70125_A;
            Vec3 v = MCH_Lib.RotVec3((double)0.0, (double)0.0, (double)(-dist), (float)yaw, (float)pitch);
            if (mc.field_71474_y.field_74320_O == 2) {
                v.field_72450_a = -v.field_72450_a;
                v.field_72448_b = -v.field_72448_b;
                v.field_72449_c = -v.field_72449_c;
            }
            Vec3 vs = Vec3.func_72443_a((double)viewer.field_70165_t, (double)(viewer.field_70163_u + (double)viewer.func_70047_e()), (double)viewer.field_70161_v);
            MovingObjectPosition mop = mc.field_71451_h.field_70170_p.func_72933_a(vs.func_72441_c(0.0, 0.0, 0.0), vs.func_72441_c(v.field_72450_a, v.field_72448_b, v.field_72449_c));
            double block_dist = dist;
            if (mop != null && mop.field_72313_a == MovingObjectPosition.MovingObjectType.BLOCK && (block_dist = vs.func_72438_d(mop.field_72307_f) - 0.4) < 0.0) {
                block_dist = 0.0;
            }
            GL11.glTranslated((double)(v.field_72450_a * (block_dist / dist)), (double)(v.field_72448_b * (block_dist / dist)), (double)(v.field_72449_c * (block_dist / dist)));
            ix += v.field_72450_a * (block_dist / dist);
            iy += v.field_72448_b * (block_dist / dist);
            iz += v.field_72449_c * (block_dist / dist);
        }
        if ((scale = Math.sqrt((px = (double)((float)(this.field_70169_q + (this.field_70165_t - this.field_70169_q) * (double)par2 - ix))) * px + (py = (double)((float)(this.field_70167_r + (this.field_70163_u - this.field_70167_r) * (double)par2 - iy))) * py + (pz = (double)((float)(this.field_70166_s + (this.field_70161_v - this.field_70166_s) * (double)par2 - iz))) * pz) / 10.0) < 1.0) {
            scale = 1.0;
        }
        MCH_GuiTargetMarker.addMarkEntityPos((int)100, (Entity)this, (double)(px / scale), (double)(py / scale), (double)(pz / scale), (boolean)false);
        GL11.glPopMatrix();
    }
}

