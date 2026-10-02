package com.dogpound.prideguard.mixin;

import com.dogpound.prideguard.Guard;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PrideGuard's packet taps. Each one runs right AFTER vanilla's checkThreadAndEnqueue — i.e. on the server thread,
 * in the order the client sent them — and can cancel the packet (a cheat that never happens) before vanilla acts.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {
    @Shadow(remap = false) public EntityPlayerMP field_147369_b; // player

    private static final String ENQUEUE = "Lnet/minecraft/network/PacketThreadUtil;func_180031_a(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V";

    @Inject(method = "func_147347_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$move(CPacketPlayer p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    // teleport confirmations: only recorded (ends the teleport grace early), never cancelled
    @Inject(method = "func_184339_a", remap = false, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$confirmTp(net.minecraft.network.play.client.CPacketConfirmTeleport p, CallbackInfo ci) { Guard.in(field_147369_b, p); }

    @Inject(method = "func_147345_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$dig(CPacketPlayerDigging p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_184337_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$useOnBlock(CPacketPlayerTryUseItemOnBlock p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147340_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$useEntity(CPacketUseEntity p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147351_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$click(CPacketClickWindow p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147339_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$transaction(CPacketConfirmTransaction p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_175087_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$swing(CPacketAnimation p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147355_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$slot(CPacketHeldItemChange p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147357_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$action(CPacketEntityAction p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147346_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$useItem(CPacketPlayerTryUseItem p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147349_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$payload(CPacketCustomPayload p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147343_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$sign(CPacketUpdateSign p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147341_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$tab(CPacketTabComplete p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    @Inject(method = "func_147344_a", remap = false, cancellable = true, at = @At(value = "INVOKE", target = ENQUEUE, shift = At.Shift.AFTER, remap = false))
    private void pg$creative(CPacketCreativeInventoryAction p, CallbackInfo ci) { if (Guard.in(field_147369_b, p)) ci.cancel(); }

    /** every server-side teleport (commands, pearls, elevators, respawn...): movement checks wait for the player to land */
    @Inject(method = "func_175089_a", remap = false, at = @At("HEAD"))
    private void pg$teleport(double x, double y, double z, float yaw, float pitch, java.util.Set<?> flags, CallbackInfo ci) { Guard.teleported(field_147369_b); }
}
