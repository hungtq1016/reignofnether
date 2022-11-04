package com.solegendary.reignofnether.unit.villagers;

import com.mojang.serialization.Dynamic;
import com.solegendary.reignofnether.hud.AbilityButton;
import com.solegendary.reignofnether.unit.PopulationCosts;
import com.solegendary.reignofnether.unit.Unit;
import com.solegendary.reignofnether.unit.goals.SelectedTargetGoal;
import com.solegendary.reignofnether.unit.goals.MoveToCursorBlockGoal;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class VillagerUnit extends Vindicator implements Unit {
    // region
    public List<AbilityButton> getAbilities() {return abilities;};

    public MoveToCursorBlockGoal getMoveGoal() {return moveGoal;}
    public void setMoveGoal(MoveToCursorBlockGoal moveGoal) {this.moveGoal = moveGoal;}
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() {return targetGoal;}
    public void setTargetGoal(SelectedTargetGoal<? extends LivingEntity> targetGoal) {this.targetGoal = targetGoal;}

    public MoveToCursorBlockGoal moveGoal;
    public SelectedTargetGoal<? extends LivingEntity> targetGoal;

    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }

    // if true causes moveGoal and attackGoal to work together to allow attack moving
    // moves to a block but will chase/attack nearby monsters in range up to a certain distance away
    private BlockPos attackMoveTarget = null;
    private LivingEntity followTarget = null; // if nonnull, continuously moves to the target
    private boolean holdPosition = false;

    // which player owns this unit? this format ensures its synched to client without having to use packets
    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(VillagerUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
    }

    // combat stats
    public boolean getWillRetaliate() {return willRetaliate;}
    public int getAttackCooldown() {return attackCooldown;}
    public float getAggroRange() {return aggroRange;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle;}
    public float getAttackRange() {return attackRange;}
    public float getSpeedModifier() {return speedModifier;}
    public float getDamage() {return damage;}
    public float getSightRange() {return sightRange;}
    public int getPopCost() {return popCost;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    final public float damage = 0.0f;
    final public float speedModifier = 1.0f;
    final public float attackRange = 0; // only used by ranged units
    final public int attackCooldown = 20;
    final public float aggroRange = 10;
    final public float sightRange = 10f;
    final public boolean willRetaliate = false; // will attack when hurt by an enemy
    final public boolean aggressiveWhenIdle = false;
    final public int popCost = PopulationCosts.VILLAGER;

    private static final List<AbilityButton> abilities = new ArrayList<>();

    public VillagerUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    // overwrite regular movespeed of 0.5 (way too fast!)
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    public void tick() {
        super.tick();
        Unit.tick(this);
    }

    @Override
    protected void registerGoals() {
        this.moveGoal = new MoveToCursorBlockGoal(this, speedModifier);
        this.targetGoal = new SelectedTargetGoal(this, true, true);

        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(3, targetGoal);
    }
}