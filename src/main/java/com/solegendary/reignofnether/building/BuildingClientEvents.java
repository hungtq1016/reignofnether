package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BuildingClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static int totalPopulationSupply = 0;
    public static int getTotalPopulationSupply() {
        return Math.min(ResourceCosts.MAX_POPULATION, totalPopulationSupply);
    }

    // clientside buildings used for tracking position (for cursor selection)
    private static final ArrayList<Building> buildings = new ArrayList<>();

    private static ArrayList<Building> selectedBuildings = new ArrayList<>();
    private static Class<? extends Building> buildingToPlace = null;
    private static Class<? extends Building> lastBuildingToPlace = null;
    private static ArrayList<BuildingBlock> blocksToDraw = new ArrayList<>();
    private static boolean replacedTexture = false;
    private static Rotation buildingRotation = Rotation.NONE;
    private static Vec3i buildingDimensions = new Vec3i(0,0,0);

    private static long lastLeftClickTime = 0; // to track double clicks
    private static final long DOUBLE_CLICK_TIME_MS = 500;

    // can only be one preselected building as you can't box-select them like units
    public static Building getPreselectedBuilding() {
        for (Building building: buildings)
            if (building.isPosInsideBuilding(CursorClientEvents.getPreselectedBlockPos()))
                return building;
        return null;
    }
    public static ArrayList<Building> getSelectedBuildings() { return selectedBuildings; }
    public static List<Building> getBuildings() {
        return buildings;
    }

    public static void setSelectedBuildings(ArrayList<Building> buildings) {
        selectedBuildings.clear();
        selectedBuildings.addAll(buildings);
        if (selectedBuildings.size() > 0)
            UnitClientEvents.setSelectedUnits(new ArrayList<>());
    }
    public static void addSelectedBuilding(Building building) {
        selectedBuildings.add(building);
        selectedBuildings.sort(Comparator.comparing(b -> b.name));
        UnitClientEvents.setSelectedUnits(new ArrayList<>());
    }

    public static void setBuildingToPlace(Class<? extends Building> building) {
        buildingToPlace = building;

        if (buildingToPlace != lastBuildingToPlace && buildingToPlace != null) {
            // load the new buildingToPlace's data
            try {
                Class<?>[] paramTypes = { LevelAccessor.class };
                Method getRelativeBlockData = buildingToPlace.getMethod("getRelativeBlockData", paramTypes);
                blocksToDraw = (ArrayList<BuildingBlock>) getRelativeBlockData.invoke(null, MC.level);
                buildingDimensions = BuildingUtils.getBuildingSize(blocksToDraw);
                buildingRotation = Rotation.NONE;
            } catch (Exception e) {
                e.printStackTrace();
            }
            lastBuildingToPlace = buildingToPlace; // avoid loading the same data twice unnecessarily
        }
    }
    public static Class<? extends Building> getBuildingToPlace() { return buildingToPlace; }

    // adds a green overlay option to OverlayTexture at (0,0)
    public static void replaceOverlayTexture() {
        NativeImage nativeimage = MC.gameRenderer.overlayTexture.texture.getPixels();
        int bgr = MiscUtil.reverseHexRGB(0x00FF00); // for some reason setPixelRGBA reads it as ABGR with A inversed
        if (nativeimage != null) {
            nativeimage.setPixelRGBA(0,0, bgr | (0xB2 << 24));
            RenderSystem.activeTexture(33985);
            MC.gameRenderer.overlayTexture.texture.bind();
            nativeimage.upload(0, 0, 0, 0, 0, nativeimage.getWidth(), nativeimage.getHeight(), false, true, false, false);
            RenderSystem.activeTexture(33984);
        }
    }

    // draws the building with a green/red overlay (based on placement validity) at the target position
    // based on whether the location is valid or not
    // location should be 1 space above the selected spot
    public static void drawBuildingToPlace(PoseStack matrix, BlockPos originPos) {
        boolean valid = isBuildingPlacementValid(originPos);

        int minX = 999999;
        int minY = 999999;
        int minZ = 999999;
        int maxX = -999999;
        int maxY = -999999;
        int maxZ = -999999;

        for (BuildingBlock block : blocksToDraw) {
            BlockRenderDispatcher renderer = MC.getBlockRenderer();
            BlockState bs = block.getBlockState();
            BlockPos bp = new BlockPos(
                    originPos.getX() + block.getBlockPos().getX(),
                    originPos.getY() + block.getBlockPos().getY(),
                    originPos.getZ() + block.getBlockPos().getZ()
            );
            // ModelData modelData = renderer.getBlockModel(bs).getModelData(MC.level, bp, bs, ModelDataManager.getModelData(MC.level, bp));

            matrix.pushPose();
            Entity cam = MC.cameraEntity;
            matrix.translate( // bp is center of block whereas render is corner, so offset by 0.5
                    bp.getX() - cam.getX(),
                    bp.getY() - cam.getY() - 0.6,
                    bp.getZ() - cam.getZ());

            // show red overlay if invalid, else show green
            renderer.renderSingleBlock(
                    bs, matrix,
                    MC.renderBuffers().crumblingBufferSource(), // don't render over other stuff
                    15728880,
                    // red if invalid, else green
                    valid ? OverlayTexture.pack(0,0) : OverlayTexture.pack(0,3));

            matrix.popPose();

            if (bp.getX() < minX) minX = bp.getX();
            if (bp.getY() < minY) minY = bp.getY();
            if (bp.getZ() < minZ) minZ = bp.getZ();
            if (bp.getX() > maxX) maxX = bp.getX();
            if (bp.getY() > maxY) maxY = bp.getY();
            if (bp.getZ() > maxZ) maxZ = bp.getZ();
        }
        // draw placement outline below
        maxX += 1;
        minY += 1.05f;
        maxZ += 1;

        float r = valid ? 0 : 1.0f;
        float g = valid ? 1.0f : 0;
        ResourceLocation rl = new ResourceLocation("forge:textures/white.png");
        AABB aabb = new AABB(minX, minY, minZ, maxX, minY, maxZ);
        MyRenderer.drawLineBox(matrix, aabb, r, g, 0,0.5f);
        MyRenderer.drawSolidBox(matrix, aabb, Direction.UP, r, g, 0, 0.5f, rl);
        AABB aabb2 = new AABB(minX, 0, minZ, maxX, minY, maxZ);
        MyRenderer.drawLineBox(matrix, aabb2, r, g, 0,0.25f);
    }

    public static boolean isBuildingPlacementValid(BlockPos originPos) {
        return !isBuildingPlacementInAir(originPos) &&
               !isBuildingPlacementClipping(originPos) &&
               !isOverlappingAnyOtherBuilding();
    }

    // disallow any building block from clipping into any other existing blocks
    public static boolean isBuildingPlacementClipping(BlockPos originPos) {
        for (BuildingBlock block : blocksToDraw) {
            Material bm = block.getBlockState().getMaterial();
            BlockPos bp = new BlockPos(
                    originPos.getX() + block.getBlockPos().getX(),
                    originPos.getY() + block.getBlockPos().getY() + 1,
                    originPos.getZ() + block.getBlockPos().getZ()
            );
            Material bmWorld = MC.level.getBlockState(bp).getMaterial();
            if ((bmWorld.isSolid() || bmWorld.isLiquid()) && (bm.isSolid() || bm.isLiquid()))
                return true;
        }
        return false;
    }

    // 90% all solid blocks at the base of the building must be on top of solid blocks to be placeable
    // excluding those under blocks which aren't solid anyway
    public static boolean isBuildingPlacementInAir(BlockPos originPos) {
        int solidBlocksBelow = 0;
        int blocksBelow = 0;
        for (BuildingBlock block : blocksToDraw) {
            if (block.getBlockPos().getY() == 0 && MC.level != null) {
                BlockPos bp = new BlockPos(
                        originPos.getX() + block.getBlockPos().getX(),
                        originPos.getY() + block.getBlockPos().getY() + 1,
                        originPos.getZ() + block.getBlockPos().getZ()
                );
                BlockState bs = block.getBlockState(); // building block
                BlockState bsBelow = MC.level.getBlockState(bp.below()); // world block

                if (bs.getMaterial().isSolid()) {
                    blocksBelow += 1;
                    if (bsBelow.getMaterial().isSolid())
                        solidBlocksBelow += 1;
                }
            }
        }
        if (blocksBelow <= 0) return false; // avoid division by 0
        return ((float) solidBlocksBelow / (float) blocksBelow) < 0.9f;
    }

    // disallow the building borders from overlapping any other's, even if they don't collide physical blocks
    // also allow for a 1 block gap between buildings so units can spawn and stairs don't have their blockstates messed up
    public static boolean isOverlappingAnyOtherBuilding() {

        BlockPos origin = getOriginPos();
        Vec3i originOffset = new Vec3i(origin.getX(), origin.getY(), origin.getZ());
        BlockPos minPos = BuildingUtils.getMinCorner(blocksToDraw).offset(originOffset).offset(-1,-1,-1);
        BlockPos maxPos = BuildingUtils.getMaxCorner(blocksToDraw).offset(originOffset).offset(1,1,1);

        for (Building building : buildings) {
            for (BuildingBlock block : building.blocks) {
                BlockPos bp = block.getBlockPos();
                if (bp.getX() >= minPos.getX() && bp.getX() <= maxPos.getX() &&
                    bp.getY() >= minPos.getY() && bp.getY() <= maxPos.getY() &&
                    bp.getZ() >= minPos.getZ() && bp.getZ() <= maxPos.getZ())
                    return true;
            }
        }
        return false;
    }

    // gets the cursor position rotated according to the preselected building
    private static BlockPos getOriginPos() {
        int xAdj = 0;
        int zAdj = 0;
        int xRadius = buildingDimensions.getX() / 2;
        int zRadius = buildingDimensions.getZ() / 2;

        switch(buildingRotation) {
            case NONE                -> { xAdj = -xRadius; zAdj = -zRadius; }
            case CLOCKWISE_90        -> { xAdj =  xRadius; zAdj = -zRadius; }
            case CLOCKWISE_180       -> { xAdj =  xRadius; zAdj =  zRadius; }
            case COUNTERCLOCKWISE_90 -> { xAdj = -xRadius; zAdj =  zRadius; }
        }
        return CursorClientEvents.getPreselectedBlockPos().offset(xAdj, 0, zAdj);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (!OrthoviewClientEvents.isEnabled())
            return;

        if (buildingToPlace != null)
            drawBuildingToPlace(evt.getPoseStack(), getOriginPos());

        Building preselectedBuilding = getPreselectedBuilding();

        totalPopulationSupply = 0;

        for (Building building : buildings) {
            AABB aabb = new AABB(
                    new BlockPos(BuildingUtils.getMinCorner(building.blocks)),
                    new BlockPos(BuildingUtils.getMaxCorner(building.blocks)).offset(1,1,1)
            );

            if (selectedBuildings.contains(building))
                MyRenderer.drawLineBox(evt.getPoseStack(), aabb, 1.0f, 1.0f, 1.0f, 1.0f);
            else if (building.equals(preselectedBuilding) && !HudClientEvents.isMouseOverAnyButtonOrHud()) {
                if (HudClientEvents.hudSelectedEntity instanceof WorkerUnit &&
                    MiscUtil.isRightClickDown(MC))
                    MyRenderer.drawLineBox(evt.getPoseStack(), aabb, 1.0f, 1.0f, 1.0f, 1.0f);
                else
                    MyRenderer.drawLineBox(evt.getPoseStack(), aabb, 1.0f, 1.0f, 1.0f, MiscUtil.isRightClickDown(MC) ? 1.0f : 0.5f);
            }


            Relationship buildingRs = getPlayerToBuildingRelationship(building);

            if (buildingRs == Relationship.OWNED && building.isBuilt)
                totalPopulationSupply += building.popSupply;

            switch (buildingRs) {
                case OWNED -> MyRenderer.drawOutlineBottom(evt.getPoseStack(), aabb, 0.3f, 1.0f, 0.3f, 0.2f);
                case FRIENDLY -> MyRenderer.drawOutlineBottom(evt.getPoseStack(), aabb, 0.3f, 0.3f, 1.0f, 0.2f);
                case HOSTILE -> MyRenderer.drawOutlineBottom(evt.getPoseStack(), aabb, 1.0f, 0.3f, 0.3f, 0.2f);
            }
        }

        // draw rally point and line
        for (Building selBuilding : selectedBuildings) {
            if (selBuilding instanceof ProductionBuilding selProdBuilding && selProdBuilding.getRallyPoint() != null) {
                float a = MiscUtil.getOscillatingFloat(0.25f,0.75f);
                MyRenderer.drawBlockFace(evt.getPoseStack(),
                        Direction.UP,
                        selProdBuilding.getRallyPoint(),
                        0, 1, 0, a);
                MyRenderer.drawLine(evt.getPoseStack(),
                        BuildingUtils.getCentrePos(selProdBuilding.getBlocks()).offset(0,-1,0),
                        selProdBuilding.getRallyPoint(),
                        0, 1, 0, a);
            }
        }
    }

    // on scroll rotate the building placement by 90deg by resorting the blocks list
    // for some reason this event is run twice every scroll
    private static boolean secondScrollEvt = true;
    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled evt) {
        secondScrollEvt = !secondScrollEvt;
        if (!secondScrollEvt) return;

        if (buildingToPlace != null) {
            Rotation rotation = evt.getScrollDelta() > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
            buildingRotation = buildingRotation.getRotated(rotation);
            for (int i = 0; i < blocksToDraw.size(); i++)
                blocksToDraw.set(i, blocksToDraw.get(i).rotate(MC.level, rotation));
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre evt) throws NoSuchFieldException, IllegalAccessException {
        if (!OrthoviewClientEvents.isEnabled())
            return;

        // prevent clicking behind HUDs
        if (HudClientEvents.isMouseOverAnyButtonOrHud()) {
            setBuildingToPlace(null);
            return;
        }

        BlockPos pos = getOriginPos();
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {
            Building preSelBuilding = getPreselectedBuilding();

            // place a new building
            if (buildingToPlace != null && isBuildingPlacementValid(pos) && MC.player != null) {
                String buildingName = (String) buildingToPlace.getField("buildingName").get(null);

                ArrayList<Integer> builderIds = new ArrayList<>();
                for (LivingEntity builderEntity : UnitClientEvents.getSelectedUnits())
                    if (builderEntity instanceof WorkerUnit)
                        builderIds.add(builderEntity.getId());

                BuildingServerboundPacket.placeBuilding(buildingName, pos, buildingRotation, MC.player.getName().getString(),
                        builderIds.stream().mapToInt(i -> i).toArray());
                setBuildingToPlace(null);
            }
            // equivalent of UnitClientEvents.onMouseClick()
            else if (buildingToPlace == null) {

                // select all nearby buildings of the same type when the same building is double-clicked
                if (selectedBuildings.size() == 1 && MC.level != null && !Keybindings.shiftMod.isDown() &&
                    (System.currentTimeMillis() - lastLeftClickTime) < DOUBLE_CLICK_TIME_MS &&
                    preSelBuilding != null && selectedBuildings.contains(preSelBuilding)) {

                    lastLeftClickTime = 0;
                    Building selBuilding = selectedBuildings.get(0);
                    BlockPos centre = BuildingUtils.getCentrePos(selBuilding.blocks);
                    ArrayList<Building> nearbyBuildings = getBuildingsWithinRange(
                            new Vec3(centre.getX(), centre.getY(), centre.getZ()),
                            OrthoviewClientEvents.getZoom() * 2,
                            selBuilding.name
                    );
                    setSelectedBuildings(new ArrayList<>());
                    for (Building building : nearbyBuildings)
                        if (getPlayerToBuildingRelationship(building) == Relationship.OWNED)
                            addSelectedBuilding(building);
                }

                // left click -> select a single building
                // if shift is held, deselect a building or add it to the selected group
                else if (preSelBuilding != null && CursorClientEvents.getLeftClickAction() == null) {
                    boolean deselected = false;

                    if (Keybindings.shiftMod.isDown())
                        deselected = selectedBuildings.remove(preSelBuilding);

                    if (Keybindings.shiftMod.isDown() && !deselected &&
                            getPlayerToBuildingRelationship(preSelBuilding) == Relationship.OWNED) {
                        addSelectedBuilding(preSelBuilding);
                    }
                    else if (!deselected) { // select a single building - this should be the only code path that allows you to select a non-owned building
                        setSelectedBuildings(new ArrayList<>());
                        addSelectedBuilding(preSelBuilding);
                    }
                }
            }

            // deselect any non-owned buildings if we managed to select them with owned buildings
            // and disallow selecting > 1 non-owned building
            if (selectedBuildings.size() > 1)
                selectedBuildings.removeIf(b -> getPlayerToBuildingRelationship(b) != Relationship.OWNED);

            lastLeftClickTime = System.currentTimeMillis();
        }
        else if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            // set rally points
            if (!Keybindings.altMod.isDown()) {
                for (Building selBuilding : selectedBuildings) {
                    if (selBuilding instanceof ProductionBuilding selProdBuilding && getPlayerToBuildingRelationship(selBuilding) == Relationship.OWNED) {
                        BlockPos rallyPoint = CursorClientEvents.getPreselectedBlockPos();
                        selProdBuilding.setRallyPoint(rallyPoint);
                        BuildingServerboundPacket.setRallyPoint(
                                selBuilding.originPos,
                                rallyPoint
                        );
                    }
                }
            }
            setBuildingToPlace(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (!replacedTexture) {
            replaceOverlayTexture();
            replacedTexture = true;
        }
        if (MC.level != null && MC.level.dimension() == Level.OVERWORLD && evt.phase == TickEvent.Phase.END) {
            for (Building building : buildings)
                building.tick(MC.level);

            // cleanup destroyed buildings
            selectedBuildings.removeIf(Building::shouldBeDestroyed);
            buildings.removeIf(Building::shouldBeDestroyed);
        }
    }

    public static ArrayList<Building> getBuildingsWithinRange(Vec3 pos, float range, String buildingName) {
        ArrayList<Building> retBuildings = new ArrayList<>();
        for (Building building : buildings) {
            if (building.name.equals(buildingName)) {
                BlockPos centre = BuildingUtils.getCentrePos(building.blocks);
                Vec3 centreVec3 = new Vec3(centre.getX(), centre.getY(), centre.getZ());
                if (pos.distanceTo(centreVec3) <= range)
                    retBuildings.add(building);
            }
        }
        return retBuildings;
    }

    // place a building clientside that has already been registered on serverside
    public static void placeBuilding(String buildingName, BlockPos pos, Rotation rotation, String ownerName) {
        for (Building building : buildings)
            if (BuildingUtils.isPosPartOfAnyBuilding(MC.level, pos, false))
                return; // building already exists clientside

        Building newBuilding = BuildingUtils.getNewBuilding(buildingName, MC.level, pos, rotation, ownerName);

        if (newBuilding != null) {
            boolean buildingExists = false;
            for (Building building : buildings)
                if (building.originPos == pos) {
                    buildingExists = true;
                    break;
                }
            if (!buildingExists)
                buildings.add(newBuilding);
        }

        // sync the goal so we can display the correct animations
        Entity entity = HudClientEvents.hudSelectedEntity;
        if (entity instanceof WorkerUnit workerUnit)
            workerUnit.getBuildRepairGoal().setBuildingTarget(newBuilding);
    }

    public static void destroyBuilding(BlockPos pos) {
        buildings.removeIf(b -> b.originPos == pos);
    }

    public static void syncBuildingBlocks(Building serverBuilding, int blocksPlaced) {
        for (Building building : buildings)
            if (building.originPos.equals(serverBuilding.originPos))
                building.serverBlocksPlaced = blocksPlaced;
    }

    public static Relationship getPlayerToBuildingRelationship(Building building) {
        if (MC.player != null && building.ownerName.equals(MC.player.getName().getString()))
            return Relationship.OWNED;
        else
            return Relationship.HOSTILE;
    }
}
