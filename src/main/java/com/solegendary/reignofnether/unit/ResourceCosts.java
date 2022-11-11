package com.solegendary.reignofnether.unit;

// defined here because we need to be able to access in both
// static (for ProductionItems) and nonstatic (for getCurrentPopulation) contexts
// and we can't declare static getters in the Unit interface
public class ResourceCosts {

    public static class Creeper {
        public static final int FOOD = 50;
        public static final int WOOD = 0;
        public static final int ORE = 100;
        public static final int TICKS = 100;
        public static final int POPULATION = 2;
    }
    public static class Skeleton {
        public static final int FOOD = 60;
        public static final int WOOD = 40;
        public static final int ORE = 0;
        public static final int TICKS = 100;
        public static final int POPULATION = 1;
    }
    public static class Zombie {
        public static final int FOOD = 100;
        public static final int WOOD = 0;
        public static final int ORE = 0;
        public static final int TICKS = 100;
        public static final int POPULATION = 1;
    }
    public static class Villager {
        public static final int FOOD = 50;
        public static final int WOOD = 0;
        public static final int ORE = 0;
        public static final int TICKS = 100;
        public static final int POPULATION = 1;
    }
    public static class VillagerHouse {
        public static final int FOOD = 0;
        public static final int WOOD = 200;
        public static final int ORE = 0;
        public static final int SUPPLY = 10;
    }
    public static class VillagerTower {
        public static final int FOOD = 0;
        public static final int WOOD = 100;
        public static final int ORE = 0;
        public static final int SUPPLY = 0;
    }
    public static class Graveyard {
        public static final int FOOD = 0;
        public static final int WOOD = 150;
        public static final int ORE = 0;
        public static final int SUPPLY = 0;
    }

    public static final int MAX_POPULATION = 100; // max possible pop you can have regardless of buildings
}