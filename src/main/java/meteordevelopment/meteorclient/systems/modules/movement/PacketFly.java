package meteordevelopment.meteorclient.systems.modules.movement;


import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.PlayerPositionLookS2CPacketAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;

public class PacketFly extends Module {
    private final HashSet<PlayerMoveC2SPacket> packets = new HashSet<>();
    private final SettingGroup sgMovement = settings.createGroup("movement");
    private final SettingGroup sgClient = settings.createGroup("client");
    private final SettingGroup sgBypass = settings.createGroup("bypass");


    private final Setting<Double> horizontalSpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal speed in blocks per tick(actually im not sure but im assuming it is).")
        .defaultValue(0.3)
        .min(0.0)
        .max(2.0)
        .sliderMin(0.0)
        .sliderMax(2.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical speed in blocks per tick.")
        .defaultValue(0.5)
        .min(0.0)
        .max(2.0)
        .sliderMin(0.0)
        .sliderMax(2.0)
        .build()
    );
    private final Setting<Integer> fallDelay = sgBypass.add(new IntSetting.Builder()
        .name("fall-delay")
        .description("How often to fall (antikick).")
        .defaultValue(4)
        .sliderMin(1)
        .sliderMax(30)
        .min(1)
        .max(30)
        .build()
    );
    private final Setting<Boolean> cancelPackets = sgBypass.add(new BoolSetting.Builder()
        .name("cancel-packets")
        .description("Cancel rubberband packets clientside.")
        .defaultValue(false)
        .build()
    );

    private Vec3d cachedPos;
    private int timer = 0;
    public PacketFly() {
        super(Categories.Movement, "packet-fly", "Fly using packets.");
    }

    @Override
    public void onActivate()
    {

        cachedPos = mc.player.getRootVehicle().getPos();
    }

    @EventHandler
    public void onSendMovementPackets(SendMovementPacketsEvent.Pre event) {
        mc.player.setVelocity(Vec3d.ZERO);
        //event.setCancelled(true);
    }

    @EventHandler
    public void onMove (PlayerMoveEvent event) {

        //event.setCancelled(true);
    }

    @EventHandler
    public void onPacketSent(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket && !packets.remove((PlayerMoveC2SPacket) event.packet)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            PlayerPositionLookS2CPacket p = (PlayerPositionLookS2CPacket) event.packet;

            //p.yaw = mc.player.getYaw();
            ///p.pitch = mc.player.getPitch();

            if (cancelPackets.get()) {
                event.setCancelled(true);
            }
        }
    }


    @EventHandler
    public void onPostTick(TickEvent.Post event) {
        if (!mc.player.isAlive())
            return;

        double hspeed = horizontalSpeed.get();
        double vspeed = verticalSpeed.get();
        timer++;

        Vec3d forward = new Vec3d(0, 0, hspeed).rotateY(-(float) Math.toRadians(mc.player.getYaw()));
        Vec3d moveVec = Vec3d.ZERO;

        if (mc.player.input.pressingForward) {
            moveVec = moveVec.add(forward);
        }
        if (mc.player.input.pressingBack) {
            moveVec = moveVec.add(forward.negate());
        }
        if (mc.player.input.jumping) {
            moveVec = moveVec.add(0, vspeed, 0);
        }
        if (mc.player.input.sneaking) {
            moveVec = moveVec.add(0, -vspeed, 0);
        }
        if (mc.player.input.pressingLeft) {
            moveVec = moveVec.add(forward.rotateY((float) Math.toRadians(90)));
        }
        if (mc.player.input.pressingRight) {
            moveVec = moveVec.add(forward.rotateY((float) -Math.toRadians(90)));
        }

        Entity target = mc.player.getRootVehicle();
        //if phase is on(it always is lol)
        //if (getSetting(0).asMode().getMode() == 0) {
        if (timer > fallDelay.get()) {
            moveVec = moveVec.add(0, -vspeed, 0);
            timer = 0;
        }

        cachedPos = cachedPos.add(moveVec);

        //target.noClip = true;
        target.updatePositionAndAngles(cachedPos.x, cachedPos.y, cachedPos.z, mc.player.getYaw(), mc.player.getPitch());
        if (target != mc.player) {
            mc.player.networkHandler.sendPacket(new VehicleMoveC2SPacket(target));
        } else {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(cachedPos.x, cachedPos.y, cachedPos.z, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(cachedPos.x, cachedPos.y - 0.01, cachedPos.z, true));
        }


    }
}
