package com.solegendary.reignofnether.research;

import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.research.researchItems.ResearchResourceCapacity;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// class to track status of research items for the client player - we generally don't care about other players' research
public class ResearchClient {

    private final static Minecraft MC = Minecraft.getInstance();

    final private static List<String> researchItems = Collections.synchronizedList(new ArrayList<>());

    public static void removeAllResearch() {
        synchronized (researchItems) {
            researchItems.clear();
        }
    }

    public static void addResearch(String ownerName, String researchItemName) {
        synchronized (researchItems) {
            if (MC.player != null && MC.player.getName().getString().equals(ownerName)) {
                researchItems.add(researchItemName);
                HudClientEvents.showTemporaryMessage(I18n.get(
                    "research.reignofnether.upgrade_completed",
                    researchItemName
                ));

                if (researchItemName.equals(ResearchResourceCapacity.itemName))
                    for (LivingEntity unit : UnitClientEvents.getAllUnits())
                        if (unit instanceof WorkerUnit)
                            ((Unit) unit).setupEquipmentAndUpgradesClient();
            }
        }
    }

    public static boolean hasResearch(String researchItemName) {
        synchronized (researchItems) {
            if (hasCheat("medievalman")) {
                return true;
            }
            for (String researchItem : researchItems)
                if (researchItem.equals(researchItemName)) {
                    return true;
                }
            return false;
        }
    }

    final private static List<String> cheatItems = Collections.synchronizedList(new ArrayList<>());

    public static void removeAllCheats() {
        synchronized (cheatItems) {
            cheatItems.clear();
        }
    }

    public static void addCheatWithValue(String cheatItemName, int value) {
    }

    public static void addCheat(String cheatItemName) {
        synchronized (cheatItems) {
            cheatItems.add(cheatItemName);
        }
    }

    public static void removeCheat(String cheatItemName) {
        synchronized (cheatItems) {
            cheatItems.removeIf(r -> r.equals(cheatItemName));
        }
    }

    public static boolean hasCheat(String cheatItemName) {
        synchronized (cheatItems) {
            for (String cheatItem : cheatItems)
                if (cheatItem.equals(cheatItemName)) {
                    return true;
                }
            return false;
        }
    }
}
