package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.Guard;
import com.dogpound.prideguard.Mode;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Her story: someone poured lava over her whole city. The research's single best anti-lava-grief rule: brand-new
 * players (under N hours played) can pour lava or start fires only on land where they're a builder or better
 * (RealmCoin land) — not in the wild, not on someone else's plot. A rule, not a guess, so Safe Mode doesn't soften
 * it; ops and longer-time players are never affected. Mode Watch = only logs who tried.
 */
public class LavaLock extends Check {
    public static int hours = 2;

    public LavaLock() { super("lavalock", "New-Player Lava Lock", "World", "brand-new players pouring lava or lighting fires outside land they build on"); mode = Mode.CANCEL; policy = true; }

    private boolean blocked(EntityPlayerMP p, BlockPos where) {
        if (mode == Mode.OFF || com.dogpound.prideguard.Perm.has(p, com.dogpound.prideguard.Perm.BYPASS) || p.isCreative()) return false;
        long played = p.getStatFile().readStat(StatList.PLAY_ONE_MINUTE) / 20 / 3600;
        if (played >= hours) return false;
        return !onOwnLand(p, where);
    }

    /** RealmCoin land: builder trust or better on that plot (wilderness = no) */
    private static boolean onOwnLand(EntityPlayer p, BlockPos pos) {
        try {
            Class<?> reg = Class.forName("com.dogpound.realmcoin.land.ParcelRegistry");
            Object r = reg.getMethod("get", net.minecraft.world.World.class).invoke(null, p.world);
            Object parcel = reg.getMethod("at", int.class, BlockPos.class).invoke(r, p.world.provider.getDimension(), pos);
            if (parcel == null) return false;
            Class<?> trust = Class.forName("com.dogpound.realmcoin.land.Parcel$Trust");
            Object builder = trust.getField("BUILDER").get(null);
            return (Boolean) Class.forName("com.dogpound.realmcoin.land.LandPerms")
                .getMethod("allowed", net.minecraft.world.World.class, EntityPlayer.class, BlockPos.class, trust).invoke(null, p.world, p, pos, builder);
        } catch (Throwable t) {
            return false;                                                   // no RealmCoin: new players simply wait the hours
        }
    }

    private boolean stop(EntityPlayerMP p, BlockPos pos, String what) {
        PlayerData d = Guard.data(p);
        boolean cancel = flag(d, 1, what + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        if (cancel) p.sendStatusMessage(new TextComponentString("§d✦ New players can't " + what + " outside land they build on for their first " + hours + " h. §7(Ask an admin if you need to.)"), true);
        return cancel;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void bucket(FillBucketEvent e) {
        if (!(e.getEntityPlayer() instanceof EntityPlayerMP) || e.getTarget() == null || e.getTarget().getBlockPos() == null) return;
        ItemStack held = e.getEmptyBucket();
        boolean lava = held.getItem() == Items.LAVA_BUCKET;
        if (!lava) { FluidStack f = FluidUtil.getFluidContained(held); lava = f != null && f.getFluid() != null && f.getFluid().getTemperature() > 600; }
        if (!lava) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityPlayer();
        BlockPos at = e.getTarget().getBlockPos().offset(e.getTarget().sideHit);
        if (blocked(p, at) && stop(p, at, "pour lava")) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void fire(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntityPlayer() instanceof EntityPlayerMP)) return;
        ItemStack s = e.getItemStack();
        if (s.getItem() != Items.FLINT_AND_STEEL && s.getItem() != Items.FIRE_CHARGE) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityPlayer();
        BlockPos at = e.getPos().offset(e.getFace() == null ? net.minecraft.util.EnumFacing.UP : e.getFace());
        if (blocked(p, at) && stop(p, at, "start fires")) e.setCanceled(true);
    }
}
