package com.deadmanshand.npc;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Someone a game can direct: walk there, look at that, stand still. The games decide; this does it. When nobody is
 * giving orders, its own idle goals take over.
 */
public abstract class Puppet extends PathAwareEntity {
    @Nullable
    Vec3d goal;
    double speed = 1.0;
    @Nullable
    Entity lookEntity;
    @Nullable
    Vec3d lookPoint;
    boolean frozen;

    protected Puppet(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        if (getNavigation() instanceof MobNavigation navigation) {
            navigation.setCanPathThroughDoors(true);
            navigation.setCanEnterOpenDoors(true);
        }
        setPersistent();
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new Orders(this));
        idleGoals();
    }

    /** What it does when nobody is directing it. */
    protected void idleGoals() {
    }

    public void go(BlockPos pos, double speed) {
        go(Vec3d.ofBottomCenter(pos), speed);
    }

    public void go(Vec3d target, double speed) {
        goal = target;
        this.speed = speed;
        frozen = false;
    }

    /** Look at someone (into their eyes, if they look back). */
    public void look(@Nullable Entity entity) {
        lookEntity = entity;
        lookPoint = null;
    }

    public void lookAt(Vec3d point) {
        lookPoint = point;
        lookEntity = null;
    }

    /** Stand still where you are. */
    public void hold() {
        goal = null;
        frozen = true;
        getNavigation().stop();
    }

    public void stopAll() {
        goal = null;
        frozen = false;
        lookEntity = null;
        lookPoint = null;
        getNavigation().stop();
    }

    public boolean arrived(double within) {
        return goal == null || squaredDistanceTo(goal) < within * within;
    }

    @Nullable
    public Vec3d goal() {
        return goal;
    }

    /** Where it's really looking: its head, not its body. */
    public static Vec3d gaze(Entity entity) {
        float yaw = entity instanceof net.minecraft.entity.LivingEntity living ? living.getHeadYaw() : entity.getYaw();
        return Vec3d.fromPolar(entity.getPitch(), yaw);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    private static final class Orders extends Goal {
        private final Puppet mob;
        private int repath;
        /** Where it was a moment ago, and how long it's been going nowhere. */
        private Vec3d was = Vec3d.ZERO;
        private int stuck;
        private int check;

        Orders(Puppet mob) {
            this.mob = mob;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return mob.goal != null || mob.frozen || mob.lookEntity != null || mob.lookPoint != null;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (mob.frozen) {
                mob.getNavigation().stop();
            } else if (mob.goal != null) {
                if (mob.squaredDistanceTo(mob.goal) < 0.5) {
                    mob.getNavigation().stop();
                    stuck = 0;
                } else {
                    boolean found = true;
                    if (--repath <= 0) {
                        repath = mob.getNavigation().isIdle() ? 10 : 20;
                        found = mob.getNavigation().startMovingTo(mob.goal.x, mob.goal.y, mob.goal.z, mob.speed);
                    }
                    if (++check >= 10) {
                        check = 0;
                        stuck = mob.getPos().squaredDistanceTo(was) < 0.04 ? stuck + 10 : 0;
                        was = mob.getPos();
                    }
                    // No path, or a path that isn't getting it anywhere: walk straight at it, and jump what's in the way
                    if (!found || mob.getNavigation().isIdle() || stuck >= 30) {
                        if (stuck >= 30) {
                            mob.getNavigation().stop();
                        }
                        mob.getMoveControl().moveTo(mob.goal.x, mob.goal.y, mob.goal.z, mob.speed);
                        if (mob.horizontalCollision && mob.isOnGround()) {
                            mob.getJumpControl().setActive();
                        }
                        if (stuck >= 80) {
                            stuck = 0;
                            repath = 0;
                        }
                    }
                }
            }
            if (mob.lookEntity != null && mob.lookEntity.isAlive()) {
                mob.getLookControl().lookAt(mob.lookEntity, 60.0F, 60.0F);
            } else if (mob.lookPoint != null) {
                mob.getLookControl().lookAt(mob.lookPoint.x, mob.lookPoint.y, mob.lookPoint.z, 60.0F, 60.0F);
            }
        }
    }
}
