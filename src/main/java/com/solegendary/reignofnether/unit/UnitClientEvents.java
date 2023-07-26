package com.solegendary.reignofnether.unit;

import com.mojang.math.Vector3d;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.ProductionBuilding;
import com.solegendary.reignofnether.building.ProductionItem;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class UnitClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    // units moused over or inside a box select
    private static final ArrayList<LivingEntity> preselectedUnits = new ArrayList<>();
    // units selected by click or box select
    private static final ArrayList<LivingEntity> selectedUnits = new ArrayList<>();
    // tracking of all existing units
    private static final ArrayList<LivingEntity> allUnits = new ArrayList<>();

    public static ArrayList<LivingEntity> getPreselectedUnits() { return preselectedUnits; }
    public static ArrayList<LivingEntity> getSelectedUnits() {
        return selectedUnits;
    }
    public static ArrayList<LivingEntity> getAllUnits() {
        return allUnits;
    }

    public static void addPreselectedUnit(LivingEntity unit) {
        if (unit instanceof Player player && (player.isSpectator() || player.isCreative()))
            return;
        if (!FogOfWarClientEvents.isInBrightChunk(unit.getOnPos()))
            return;
        preselectedUnits.add(unit);
    }
    public static void addSelectedUnit(LivingEntity unit) {
        if (!FogOfWarClientEvents.isInBrightChunk(unit.getOnPos()))
            return;
        selectedUnits.add(unit);
        selectedUnits.sort(Comparator.comparing(HudClientEvents::getSimpleEntityName));
        BuildingClientEvents.clearSelectedBuildings();
    }
    public static void clearPreselectedUnits() {
        preselectedUnits.clear();
    }
    public static void clearSelectedUnits() {
        selectedUnits.clear();
    }

    private static long lastLeftClickTime = 0; // to track double clicks
    private static final long DOUBLE_CLICK_TIME_MS = 500;

    private static boolean isLeftClickAttack() {
        return CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK;
    }

    public static ArrayList<LivingEntity> getOwnedIdleWorkers() {
        ArrayList<LivingEntity> idleWorkers = new ArrayList<>();
        for (LivingEntity entity : allUnits)
            if (entity instanceof WorkerUnit workerUnit &&
                getPlayerToEntityRelationship(entity) == Relationship.OWNED &&
                workerUnit.isIdle())
                idleWorkers.add(entity);
        return idleWorkers;
    }

    public static int getCurrentPopulation() {
        int currentPopulation = 0;
        if (MC.level != null && MC.player != null) {
            for (LivingEntity entity : allUnits) {
                if (entity instanceof Unit unit)
                    if (unit.getOwnerName().equals(MC.player.getName().getString()))
                        currentPopulation += unit.getPopCost();
            }
            for (Building building : BuildingClientEvents.getBuildings())
                if (building.ownerName.equals(MC.player.getName().getString()))
                    if (building instanceof ProductionBuilding prodBuilding)
                        for (ProductionItem prodItem : prodBuilding.productionQueue)
                            currentPopulation += prodItem.popCost;
        }
        return currentPopulation;
    }

    public static void sendUnitCommandManual(String playerName, UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos, BlockPos selectedBuildingPos) {
        if (MC.player != null && playerName.equals(MC.player.getName().getString()))
            sendUnitCommandManual(action, unitId, unitIds, preselectedBlockPos, selectedBuildingPos);
    }

    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos) {
        sendUnitCommandManual(action, unitId, unitIds, preselectedBlockPos, new BlockPos(0,0,0));
    }

    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos, BlockPos selectedBuildingPos) {
        if (MC.player != null) {
            UnitActionItem actionItem = new UnitActionItem(
                MC.player.getName().getString(),
                action, unitId, unitIds,
                preselectedBlockPos,
                selectedBuildingPos
            );
            actionItem.action(MC.level);

            PacketHandler.INSTANCE.sendToServer(new UnitServerboundPacket(
                MC.player.getName().getString(),
                action, unitId, unitIds,
                preselectedBlockPos,
                selectedBuildingPos
            ));
        }
    }

    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds) {
        sendUnitCommandManual(action, unitId, unitIds,
                new BlockPos(0,0,0),
                new BlockPos(0,0,0));
    }

    public static void sendUnitCommandManual(UnitAction action, int[] unitIds) {
        sendUnitCommandManual(action, -1, unitIds,
                new BlockPos(0,0,0),
                new BlockPos(0,0,0));
    }

    public static void sendUnitCommand(UnitAction action) {
        if (MC.player != null) {
            UnitActionItem actionItem = new UnitActionItem(
                MC.player.getName().getString(),
                action,
                preselectedUnits.size() > 0 ? preselectedUnits.get(0).getId() : -1,
                selectedUnits.stream().mapToInt(Entity::getId).toArray(),
                CursorClientEvents.getPreselectedBlockPos(),
                HudClientEvents.hudSelectedBuilding != null ? HudClientEvents.hudSelectedBuilding.originPos : new BlockPos(0,0,0)
            );
            actionItem.action(MC.level);

            PacketHandler.INSTANCE.sendToServer(new UnitServerboundPacket(
                MC.player.getName().getString(),
                action,
                preselectedUnits.size() > 0 ? preselectedUnits.get(0).getId() : -1,
                selectedUnits.stream().mapToInt(Entity::getId).toArray(),
                CursorClientEvents.getPreselectedBlockPos(),
                HudClientEvents.hudSelectedBuilding != null ? HudClientEvents.hudSelectedBuilding.originPos : new BlockPos(0,0,0)
            ));
        }
    }

    private static void resolveMoveAction() {
        // follow friendly unit
        if (preselectedUnits.size() == 1 && !targetingSelf()) {
            sendUnitCommand(UnitAction.FOLLOW);
        }
        // move to ground pos (disabled during camera manip)
        else if (!Keybindings.altMod.isDown()) {
            sendUnitCommand(UnitAction.MOVE);
        }
    }

    public static ResourceName getSelectedUnitResourceTarget() {
        Entity entity = HudClientEvents.hudSelectedEntity;
        if (entity instanceof WorkerUnit workerUnit)
            return workerUnit.getGatherResourceGoal().getTargetResourceName();
        return ResourceName.NONE;
    }

    /**
     * Update data on a unit from serverside, mainly to ensure unit HUD data is up-to-date
     * Only try to update health and pos if out of view
     */
    public static void syncUnitStats(int entityId, float health, Vec3 pos) {
        for(LivingEntity entity : allUnits) {
            if (entity.getId() == entityId && MC.level != null) {
                boolean isLoadedClientside = MC.level.getEntity(entityId) != null;
                if (!isLoadedClientside) {
                    entity.setHealth(health);
                    entity.setPos(pos);
                }
            }
        }
    }

    public static void syncWorkerUnit(int entityId, boolean idle, boolean isBuilding, ResourceName gatherName, BlockPos gatherPos, int gatherTicks) {
        for(LivingEntity entity : allUnits) {
            if (entity.getId() == entityId && MC.level != null) {
                if (entity instanceof WorkerUnit workerUnit) {
                    workerUnit.getBuildRepairGoal().setIsBuildingServerside(isBuilding);
                    workerUnit.getGatherResourceGoal().syncFromServer(gatherName, gatherPos, gatherTicks);
                    workerUnit.setIdle(idle);
                }
            }
        }
    }

    public static void syncUnitResources(int entityId, Resources res) {
        for(LivingEntity entity : allUnits) {
            if (entity.getId() == entityId && MC.level != null) {
                if (entity instanceof Unit unit) {
                    unit.getItems().clear();
                    unit.getItems().add(new ItemStack(Items.SUGAR, res.food));
                    unit.getItems().add(new ItemStack(Items.STICK, res.wood));
                    unit.getItems().add(new ItemStack(Items.STONE, res.ore));
                }
            }
        }
    }


    private static final int VIS_CHECK_TICKS_MAX = 10;
    private static int ticksToNextVisCheck = VIS_CHECK_TICKS_MAX;
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;

        ticksToNextVisCheck -= 1;

        if (ticksToNextVisCheck <= 0) {
            ticksToNextVisCheck = VIS_CHECK_TICKS_MAX;

            // prevent selection of units out of view
            selectedUnits.removeIf(e -> !FogOfWarClientEvents.isInBrightChunk(e.getOnPos()));
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveEvent(EntityLeaveLevelEvent evt) {
        // set the whole model visible again if it leaves while selected, or else we get a bug where only the head shows until reselected
        if (evt.getEntity() == HudClientEvents.hudSelectedEntity)
            if (HudClientEvents.portraitRendererUnit != null)
                HudClientEvents.portraitRendererUnit.setNonHeadModelVisibility(true);

        // SINGLEPLAYER ONLY - client log out: remove all entities so we don't duplicate on logging back in
        if (MC.player != null && evt.getEntity().getId() == MC.player.getId()) {
            selectedUnits.clear();
            preselectedUnits.clear();
            allUnits.clear();
        }
    }
    /**
     * Clientside entities will join and leave based on render distance, but we want to keep entities tracked at all times
     * Therefore, only remove entities if they leave serverside via UnitClientboundPacket.
     */
    public static void onEntityLeave(int entityId) {
        selectedUnits.removeIf(e -> e.getId() == entityId);
        //System.out.println("selectedUnits removed entity: " + entityId);
        preselectedUnits.removeIf(e -> e.getId() == entityId);
        //System.out.println("preselectedUnits removed entity: " + entityId);
        allUnits.removeIf(e -> e.getId() == entityId);
        //System.out.println("allUnits removed entity: " + entityId);
    }
    /**
     * Add and update entities from clientside action
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        Entity entity = evt.getEntity();
        if (entity instanceof Unit unit && evt.getLevel().isClientSide) {

            if (selectedUnits.removeIf(e -> e.getId() == entity.getId()))
                selectedUnits.add((LivingEntity) entity);
            if (preselectedUnits.removeIf(e -> e.getId() == entity.getId()))
                preselectedUnits.add((LivingEntity) entity);
            allUnits.removeIf(e -> e.getId() == entity.getId());
            allUnits.add((LivingEntity) entity);

            unit.initialiseGoals(); // for clientside data tracking - server automatically does this via registerGoals();
            unit.setupEquipmentAndUpgradesClient();
        }
    }


    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Post evt) {
        if (!OrthoviewClientEvents.isEnabled()) return;

        // prevent clicking behind HUDs
        if (HudClientEvents.isMouseOverAnyButtonOrHud()) {
            CursorClientEvents.setLeftClickAction(null);
            return;
        }

        // Can only detect clicks client side but only see and modify goals serverside so produce entity queues here
        // and consume in onWorldTick; we also can't add entities directly as they will not have goals populated
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {

            if (selectedUnits.size() > 0 && isLeftClickAttack()) {
                // A + left click -> force attack single unit (even if friendly)
                if (preselectedUnits.size() == 1 && !targetingSelf()) {
                    sendUnitCommand(UnitAction.ATTACK);
                }
                // A + left click -> force attack building (even if friendly)
                else if (BuildingClientEvents.getPreselectedBuilding() != null)
                    sendUnitCommand(UnitAction.ATTACK_BUILDING);
                // A + left click -> attack move ground
                else
                    sendUnitCommand(UnitAction.ATTACK_MOVE);
            }

            // select all nearby units of the same type when the same unit is double-clicked
            // only works for owned units
            else if (selectedUnits.size() == 1 && MC.level != null && !Keybindings.shiftMod.isDown() &&
               (System.currentTimeMillis() - lastLeftClickTime) < DOUBLE_CLICK_TIME_MS &&
                preselectedUnits.size() > 0 && selectedUnits.contains(preselectedUnits.get(0))) {

                lastLeftClickTime = 0;
                LivingEntity selectedUnit = selectedUnits.get(0);
                List<? extends LivingEntity> nearbyEntities = MiscUtil.getEntitiesWithinRange(
                        new Vector3d(selectedUnit.position().x, selectedUnit.position().y, selectedUnit.position().z),
                        OrthoviewClientEvents.getZoom(),
                        selectedUnits.get(0).getClass(),
                        MC.level
                );
                if (getPlayerToEntityRelationship(selectedUnit) == Relationship.OWNED) {
                    clearSelectedUnits();
                    for (LivingEntity entity : nearbyEntities)
                        if (getPlayerToEntityRelationship(entity) == Relationship.OWNED)
                            addSelectedUnit(entity);
                }
            }
            // move on left click
            else if (CursorClientEvents.getLeftClickAction() == UnitAction.MOVE)
                resolveMoveAction();
            // resolve any other abilities not explicitly covered here
            else if (CursorClientEvents.getLeftClickAction() != null)
                sendUnitCommand(CursorClientEvents.getLeftClickAction());

            // left click -> select a single unit
            // if shift is held, deselect a unit or add it to the selected group
            else if (preselectedUnits.size() == 1 && !isLeftClickAttack()) {
                boolean deselected = false;

                if (Keybindings.shiftMod.isDown())
                    deselected = selectedUnits.removeIf(id -> id.equals(preselectedUnits.get(0)));

                if (Keybindings.shiftMod.isDown() && !deselected &&
                    preselectedUnits.get(0) instanceof Unit &&
                    getPlayerToEntityRelationship(preselectedUnits.get(0)) == Relationship.OWNED) {
                        addSelectedUnit(preselectedUnits.get(0));
                }
                else if (!deselected) { // select a single unit - this should be the only code path that allows you to select a non-owned unit
                    clearSelectedUnits();
                    addSelectedUnit(preselectedUnits.get(0));
                }
            }
            // deselect any non-owned units if we managed to select them with owned units
            // and disallow selecting > 1 non-owned unit or the client player
            if (selectedUnits.size() > 1)
                selectedUnits.removeIf(e -> getPlayerToEntityRelationship(e) != Relationship.OWNED || e.getId() == MC.player.getId());

            lastLeftClickTime = System.currentTimeMillis();
        }
        else if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            if (selectedUnits.size() > 0) {
                Building preSelBuilding = BuildingClientEvents.getPreselectedBuilding();

                // right click -> attack unfriendly unit
                if (preselectedUnits.size() == 1 &&
                    !targetingSelf() &&
                    getPlayerToEntityRelationship(preselectedUnits.get(0)) == Relationship.HOSTILE) {

                    sendUnitCommand(UnitAction.ATTACK);
                }
                // right click -> attack unfriendly building
                else if (HudClientEvents.hudSelectedEntity instanceof AttackerUnit &&
                        (preSelBuilding != null) &&
                        BuildingClientEvents.getPlayerToBuildingRelationship(preSelBuilding) == Relationship.HOSTILE) {
                    sendUnitCommand(UnitAction.ATTACK_BUILDING);
                }
                // right click -> return resources
                else if (HudClientEvents.hudSelectedEntity instanceof Unit unit &&
                        unit.getReturnResourcesGoal() != null &&
                        Resources.getTotalResourcesFromItems(unit.getItems()).getTotalValue() > 0 &&
                        preSelBuilding != null && preSelBuilding.canAcceptResources && preSelBuilding.isBuilt &&
                        BuildingClientEvents.getPlayerToBuildingRelationship(preSelBuilding) == Relationship.OWNED) {
                    sendUnitCommand(UnitAction.RETURN_RESOURCES);
                }
                // right click -> build or repair preselected building
                else if (HudClientEvents.hudSelectedEntity instanceof WorkerUnit &&
                        preSelBuilding != null && BuildingClientEvents.getPlayerToBuildingRelationship(preSelBuilding) == Relationship.OWNED) {

                    if (preSelBuilding.name.contains(" Farm") && preSelBuilding.isBuilt)
                        sendUnitCommand(UnitAction.FARM);
                    else
                        sendUnitCommand(UnitAction.BUILD_REPAIR);
                }
                // right click -> follow friendly unit or go to preselected blockPos
                else
                    resolveMoveAction();
            }
        }
        // clear all cursor actions
        CursorClientEvents.setLeftClickAction(null);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (MC.level == null)
            return;

        /**
         *  TODO: make these visible to 1st-person players but currently had a visual glitch
         *  doesnt align to camera very well, sometimes sinks below ground and too thin
         *  TODO: make this visible behind blocks (but only seems to work if orthoview is creative mode)
         */
        // if orthoview uses creative mode: RenderLevelStageEvent.Stage.AFTER_WEATHER
        // if orthoview uses spectator mode: RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS

        if ((OrthoviewClientEvents.isEnabled() && evt.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) ||
            (!OrthoviewClientEvents.isEnabled() && evt.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS))
        {
            for (LivingEntity entity : allUnits) {
                if (!FogOfWarClientEvents.isInBrightChunk(entity.getOnPos()))
                    continue;

                Relationship unitRs = getPlayerToEntityRelationship(entity);

                float alpha = 0.5f;
                if (selectedUnits.stream().map(u -> u.getId()).toList().contains(entity.getId()))
                    alpha = 1.0f;

                // draw only the bottom of the outline boxes
                AABB entityAABB = entity.getBoundingBox();
                entityAABB = entityAABB.setMaxY(entityAABB.minY);
                boolean excludeMaxY = OrthoviewClientEvents.isEnabled();

                // always-shown highlights to indicate unit relationships
                switch (unitRs) {
                    case OWNED -> MyRenderer.drawLineBoxOutlineOnly(evt.getPoseStack(), entityAABB, 0.2f, 1.0f, 0.2f, alpha, excludeMaxY);
                    case FRIENDLY -> MyRenderer.drawLineBoxOutlineOnly(evt.getPoseStack(), entityAABB, 0.2f, 0.2f, 1.0f, alpha, excludeMaxY);
                    case HOSTILE -> MyRenderer.drawLineBoxOutlineOnly(evt.getPoseStack(), entityAABB, 1.0f, 0.2f, 0.2f, alpha, excludeMaxY);
                }
            }
        }

        if (OrthoviewClientEvents.isEnabled() && evt.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            ArrayList<LivingEntity> selectedUnits = getSelectedUnits();
            ArrayList<LivingEntity> preselectedUnits = getPreselectedUnits();

            Set<Entity> unitsToDraw = new HashSet<>();
            unitsToDraw.addAll(selectedUnits);
            unitsToDraw.addAll(preselectedUnits);

            // draw outlines on all (pre)selected units but only draw once per unit based on conditions
            // don't render preselection outlines if mousing over HUD
            if (OrthoviewClientEvents.isEnabled()) {
                for (Entity entity : unitsToDraw) {
                    if (!FogOfWarClientEvents.isInBrightChunk(entity.getOnPos()))
                        continue;

                    if (preselectedUnits.contains(entity) &&
                            isLeftClickAttack() &&
                            !targetingSelf() && !HudClientEvents.isMouseOverAnyButtonOrHud())
                        MyRenderer.drawLineBoxOutlineOnly(evt.getPoseStack(), entity.getBoundingBox(), 1.0f, 0.3f, 0.3f, 1.0f, false);
                    else if (selectedUnits.contains(entity))
                        MyRenderer.drawLineBoxOutlineOnly(evt.getPoseStack(), entity.getBoundingBox(), 1.0f, 1.0f, 1.0f, 1.0f, false);
                    else if (preselectedUnits.contains(entity) && !HudClientEvents.isMouseOverAnyButtonOrHud())
                        MyRenderer.drawLineBoxOutlineOnly(evt.getPoseStack(), entity.getBoundingBox(),1.0f, 1.0f, 1.0f, MiscUtil.isRightClickDown(MC) ? 1.0f : 0.5f, false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onButtonPress(ScreenEvent.KeyPressed.Pre evt) {
        if (evt.getKeyCode() == GLFW.GLFW_KEY_DELETE) {
            LivingEntity entity = HudClientEvents.hudSelectedEntity;
            if (entity != null && getPlayerToEntityRelationship(entity) == Relationship.OWNED)
                sendUnitCommand(UnitAction.DELETE);
        }
    }

    public static boolean targetingSelf() {
        return selectedUnits.size() == 1 &&
                preselectedUnits.size() == 1 &&
                selectedUnits.get(0).equals(preselectedUnits.get(0));
    }

    public static Relationship getPlayerToEntityRelationship(LivingEntity entity) {
        if (MC.level != null) {

            if (entity instanceof Player)
                return Relationship.HOSTILE;
            else if (!(entity instanceof Unit))
                return Relationship.NEUTRAL;

            String ownerName = ((Unit) entity).getOwnerName();

            if (ownerName.equals(MC.player.getName().getString()))
                return Relationship.OWNED;
            else
                return Relationship.HOSTILE;
        }
        return Relationship.NEUTRAL;
    }

    public static void syncConvertedUnits(String ownerName, int[] oldUnitIds, int[] newUnitIds) {

        for (int i = 0; i < oldUnitIds.length; i++) {
            if (MC.level == null)
                break;

            Entity oldEntity = MC.level.getEntity(oldUnitIds[i]);
            Entity newEntity = MC.level.getEntity(newUnitIds[i]);
            if (oldEntity instanceof Unit oldUnit &&
                newEntity instanceof Unit newUnit) {

                // retain selections
                int j = i;
                if (selectedUnits.removeIf(e -> e.getId() == oldUnitIds[j]))
                    selectedUnits.add((LivingEntity) newEntity);

                // retain control groups
                HudClientEvents.convertControlGroups(oldUnitIds, newUnitIds);

                if (oldUnit.getTargetGoal().getTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.ATTACK,
                        oldUnit.getTargetGoal().getTarget().getId(),
                        new int[] { newEntity.getId() }
                    );
                if (oldUnit.getFollowTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.FOLLOW,
                        oldUnit.getFollowTarget().getId(),
                        new int[] { newEntity.getId() }
                    );
                if (oldUnit.getMoveGoal().getMoveTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.MOVE, -1,
                        new int[] { newEntity.getId() },
                        oldUnit.getMoveGoal().getMoveTarget()
                    );
                if (oldUnit.getReturnResourcesGoal().getBuildingTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.RETURN_RESOURCES, -1,
                        new int[] { newEntity.getId() },
                        oldUnit.getReturnResourcesGoal().getBuildingTarget().originPos,
                        new BlockPos(0,0,0)
                    );
            }
            if (oldEntity instanceof AttackerUnit oldAUnit &&
                newEntity instanceof AttackerUnit newAUnit) {

                if (oldAUnit.getAttackMoveTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.ATTACK_MOVE, -1,
                        new int[] { newEntity.getId() },
                        oldAUnit.getAttackMoveTarget()
                    );
            }
        }
        // for some reason if we don't discard here first the vehicle also gets discarded
        if (MC.level != null) {
            for (int id : oldUnitIds) {
                Entity e = MC.level.getEntity(id);
                if (e != null)
                    e.discard();
            }
        }
        sendUnitCommandManual(UnitAction.DISCARD, oldUnitIds);
    }

    public static void syncEvokerCasting(int entityId, boolean startCasting) {
        for (LivingEntity entity : getAllUnits()) {
            if (entity instanceof EvokerUnit eUnit && eUnit.getId() == entityId) {
                // skip if it's your evoker since it'll already be synced
                if (MC.player != null && eUnit.getOwnerName().equals(MC.player.getName().getString()))
                    return;

                if (eUnit.getCastFangsLineGoal() != null) {
                    if (startCasting)
                        eUnit.getCastFangsLineGoal().startCasting();
                    else
                        eUnit.getCastFangsLineGoal().stop();
                }
            }
        }
    }


}
