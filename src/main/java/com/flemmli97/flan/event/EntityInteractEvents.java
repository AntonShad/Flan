package com.flemmli97.flan.event;

import com.flemmli97.flan.claim.BlockToPermissionMap;
import com.flemmli97.flan.claim.ClaimStorage;
import com.flemmli97.flan.claim.EnumPermission;
import com.flemmli97.flan.claim.IPermissionContainer;
import com.flemmli97.flan.mixin.IPersistentProjectileVars;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EntityInteractEvents {

    public static ActionResult attackEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        return attackSimple(player, entity, true);
    }

    public static ActionResult useAtEntity(PlayerEntity player, World world, Hand hand, Entity entity, /* Nullable */ EntityHitResult hitResult) {
        if (player.world.isClient || player.isSpectator())
            return ActionResult.PASS;
        ClaimStorage storage = ClaimStorage.get((ServerWorld) world);
        BlockPos pos = entity.getBlockPos();
        IPermissionContainer claim = storage.getForPermissionCheck(pos);
        if (claim != null) {
            if (entity instanceof ArmorStandEntity) {
                if (!claim.canInteract((ServerPlayerEntity) player, EnumPermission.ARMORSTAND, pos, true))
                    return ActionResult.FAIL;
            }
        }
        return ActionResult.PASS;
    }

    public static ActionResult useEntity(PlayerEntity p, World world, Hand hand, Entity entity) {
        if (p.world.isClient || p.isSpectator())
            return ActionResult.PASS;
        ServerPlayerEntity player = (ServerPlayerEntity) p;
        ClaimStorage storage = ClaimStorage.get((ServerWorld) world);
        BlockPos pos = entity.getBlockPos();
        IPermissionContainer claim = storage.getForPermissionCheck(pos);
        if (claim != null) {
            if (entity instanceof BoatEntity)
                return claim.canInteract(player, EnumPermission.BOAT, pos, true) ? ActionResult.PASS : ActionResult.FAIL;
            if (entity instanceof AbstractMinecartEntity) {
                if (entity instanceof StorageMinecartEntity)
                    return claim.canInteract(player, EnumPermission.OPENCONTAINER, pos, true) ? ActionResult.PASS : ActionResult.FAIL;
                return claim.canInteract(player, EnumPermission.MINECART, pos, true) ? ActionResult.PASS : ActionResult.FAIL;
            }
            if (entity instanceof VillagerEntity)
                return claim.canInteract(player, EnumPermission.TRADING, pos, true) ? ActionResult.PASS : ActionResult.FAIL;
            if (entity instanceof ItemFrameEntity)
                return claim.canInteract(player, EnumPermission.ITEMFRAMEROTATE, pos, true) ? ActionResult.PASS : ActionResult.FAIL;
            if(entity instanceof TameableEntity){
                TameableEntity tame = (TameableEntity) entity;
                if(tame.isOwner(player))
                    return ActionResult.PASS;
            }
            return claim.canInteract(player, EnumPermission.ANIMALINTERACT, pos, true) ? ActionResult.PASS : ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    public static boolean projectileHit(ProjectileEntity proj, HitResult res) {
        if (proj.world.isClient)
            return false;
        Entity owner = proj.getOwner();
        if (owner instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) owner;
            if (res.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockRes = (BlockHitResult) res;
                BlockPos pos = blockRes.getBlockPos();
                BlockState state = proj.world.getBlockState(pos);
                EnumPermission perm = BlockToPermissionMap.getFromBlock(state.getBlock());
                if (proj instanceof EnderPearlEntity)
                    perm = EnumPermission.ENDERPEARL;
                if (perm != EnumPermission.ENDERPEARL && perm != EnumPermission.TARGETBLOCK && perm != EnumPermission.PROJECTILES)
                    return false;
                ClaimStorage storage = ClaimStorage.get((ServerWorld) proj.world);
                IPermissionContainer claim = storage.getForPermissionCheck(pos);
                if (claim == null)
                    return false;
                boolean flag = !claim.canInteract(player, perm, pos, true);
                if (flag) {
                    if (proj instanceof PersistentProjectileEntity) {
                        PersistentProjectileEntity pers = (PersistentProjectileEntity) proj;
                        ((IPersistentProjectileVars) pers).setInBlockState(pers.world.getBlockState(pos));
                        Vec3d vec3d = blockRes.getPos().subtract(pers.getX(), pers.getY(), pers.getZ());
                        pers.setVelocity(vec3d);
                        Vec3d vec3d2 = vec3d.normalize().multiply(0.05000000074505806D);
                        pers.setPos(pers.getX() - vec3d2.x, pers.getY() - vec3d2.y, pers.getZ() - vec3d2.z);
                        pers.playSound(((IPersistentProjectileVars) pers).getSoundEvent(), 1.0F, 1.2F / (pers.world.random.nextFloat() * 0.2F + 0.9F));
                        ((IPersistentProjectileVars) pers).setInGround(true);
                        pers.shake = 7;
                        pers.setCritical(false);
                        pers.setPierceLevel((byte) 0);
                        pers.setSound(SoundEvents.ENTITY_ARROW_HIT);
                        pers.setShotFromCrossbow(false);
                        ((IPersistentProjectileVars) pers).resetPiercingStatus();
                    }
                    //find a way to properly update chorus fruit break on hit
                    //player.getServer().send(new ServerTask(player.getServer().getTicks()+2, ()->player.world.updateListeners(pos, state, state, 2)));
                }
                return flag;
            } else if (res.getType() == HitResult.Type.ENTITY) {
                if (proj instanceof EnderPearlEntity) {
                    ClaimStorage storage = ClaimStorage.get((ServerWorld) proj.world);
                    IPermissionContainer claim = storage.getForPermissionCheck(proj.getBlockPos());
                    return claim.canInteract(player, EnumPermission.ENDERPEARL, proj.getBlockPos(), true);
                }
                return attackSimple(player, ((EntityHitResult) res).getEntity(), true) != ActionResult.PASS;
            }
        }
        return false;
    }

    public static boolean preventDamage(LivingEntity entity, DamageSource source) {
        if (source.getAttacker() instanceof ServerPlayerEntity)
            return attackSimple((ServerPlayerEntity) source.getAttacker(), entity, false) != ActionResult.PASS;
        else if (source.isExplosive() && !entity.world.isClient) {
            IPermissionContainer claim = ClaimStorage.get((ServerWorld) entity.world).getForPermissionCheck(entity.getBlockPos());
            return claim != null && !claim.canInteract(null, EnumPermission.EXPLOSIONS, entity.getBlockPos());
        }
        return false;
    }

    public static ActionResult attackSimple(PlayerEntity p, Entity entity, boolean message) {
        if (p.world.isClient || p.isSpectator())
            return ActionResult.PASS;
        if (entity instanceof Monster)
            return ActionResult.PASS;
        ServerPlayerEntity player = (ServerPlayerEntity) p;
        ClaimStorage storage = ClaimStorage.get(player.getServerWorld());
        BlockPos pos = entity.getBlockPos();
        IPermissionContainer claim = storage.getForPermissionCheck(pos);
        if (claim != null) {
            if (entity instanceof ArmorStandEntity || entity instanceof MinecartEntity || entity instanceof BoatEntity || entity instanceof ItemFrameEntity)
                return claim.canInteract(player, EnumPermission.BREAKNONLIVING, pos, message) ? ActionResult.PASS : ActionResult.FAIL;
            if (entity instanceof PlayerEntity)
                return claim.canInteract(player, EnumPermission.HURTPLAYER, pos, message) ? ActionResult.PASS : ActionResult.FAIL;
            return claim.canInteract(player, EnumPermission.HURTANIMAL, pos, message) ? ActionResult.PASS : ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    public static boolean xpAbsorb(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity) {
            ClaimStorage storage = ClaimStorage.get((ServerWorld) player.world);
            BlockPos pos = player.getBlockPos();
            IPermissionContainer claim = storage.getForPermissionCheck(pos);
            if (claim != null)
                return !claim.canInteract((ServerPlayerEntity) player, EnumPermission.XP, pos, false);
        }
        return false;
    }

    public static boolean witherCanDestroy(WitherEntity wither) {
        if (wither.world.isClient)
            return true;
        ClaimStorage storage = ClaimStorage.get((ServerWorld) wither.world);
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                if (storage.getForPermissionCheck(wither.getBlockPos().add(x, 0, z)) != null)
                    return false;
            }
        return true;
    }
}
