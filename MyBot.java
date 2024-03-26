package MyBot;

import CustomUnitClasses.AbstractionLayerAI;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;


public class MyBot extends AbstractionLayerAI {
    
    private class Base {
        public Base() {
            currentBase = basesIterator.hasNext() ? basesIterator.next() : null;
            if (currentBase == null)
                return;

            int desiredWorkers = findAdjacentCells(
                    findUnitsWithin(resources, currentBase, (int) Math.floor(Math.sqrt(board.size))))
                    .size();

            boolean needsWorkers = (playerWorkers.size() < desiredWorkers
                    || playerWorkers.size() <= enemyWorkers.size()) && playerWorkers.size() < 10;

            // Make sure we can afford a barracks while maintaining a worker
            boolean enoughResources = player
                    .getResources() >= (currentBarracks == null ? barracksType.cost + workerType.cost
                            : workerType.cost);

            if ((needsWorkers && enoughResources)) {
                train(currentBase, workerType);
            }
        }
    }

    private class Barracks {
        public Barracks() {
            currentBarracks = barracksIterator.hasNext() ? barracksIterator.next() : null;
            if (currentBarracks == null)
                return;

            train(currentBarracks, chooseUnit());
        }

        private UnitType chooseUnit() {
            if (player.getResources() >= lightType.cost) { //only use lights and ranged
                return lightType;
            } else if (player.getResources() >= rangedType.cost) {
                return rangedType;
            }

            return lightType;
        }
    }

    private class Workers {
        public Workers() {
            // Assign workers to build barracks
            if (workersIterator.hasNext() && shouldBuildBarracks(player) && playerWorkers.size() >= 2) {
                currentWorker = workersIterator.next();
                assignToBuild(currentWorker, barracksType);
            }

            // Assign workers to harvest
            int numHarvesters = 0;
            if (currentBase != null) {
                List<Unit> nearbyResources = findUnitsWithin(resources, currentBase,
                        (int) Math.floor(Math.sqrt(board.size)));
                int neededHarvesters = (int) Math.floor(findAdjacentCells(nearbyResources).size() / 2);
                if (neededHarvesters == 1)
                    neededHarvesters = 2;

                while (workersIterator.hasNext() && numHarvesters < neededHarvesters) {
                    numHarvesters++;
                    Unit worker = workersIterator.next();
                    assignToHarvest(worker);
                }
            }

            while (workersIterator.hasNext()) {
                Unit worker = workersIterator.next();
                assignToAttack(worker);
            }
        }

        private void assignToAttack(Unit worker) {
            Unit closestEnemy = findClosest(enemyUnits, worker);
            if (closestEnemy != null)
                attack(worker, closestEnemy);
        }

        private void assignToBuild(Unit worker, UnitType buildingType) {
            if (playerBases.isEmpty())
                return;

            if (buildingType == barracksType) {
                Unit ownBase = playerBases.get(0);

                Unit enemyBase = findClosest(enemyBases, ownBase);

                int buildX = ownBase.getX();
                int buildY = ownBase.getY();

                if (enemyBase != null) {
                    buildX += (enemyBase.getX() > ownBase.getX()) ? -2 : 2;
                    buildY += (enemyBase.getY() > ownBase.getY()) ? 1 : -1;
                } else {
                    buildX += 1;
                }

                buildX = Math.max(0, Math.min(buildX, board.x - 1));
                buildY = Math.max(0, Math.min(buildY, board.y - 1));

                build(worker, buildingType, buildX, buildY);
            }
        }

        private void assignToHarvest(Unit worker) {
            Unit closestResource = findClosestWithin(resources, worker,
                    (int) Math.floor(findAdjacentCells(resources).size() / 2));
            Unit closestBase = findClosest(playerBases, worker);

            if (closestResource != null && closestBase != null) {
                harvest(worker, closestResource, closestBase);
            }
        }

        private boolean shouldBuildBarracks(Player player) {
            return player.getResources() >= barracksType.cost && playerBarracks.isEmpty();
        }
    }

    private class Light {
        public Light() {
            playerLights.forEach(light -> {
                assignToAttack(light);
            });
        }

        private void assignToAttack(Unit light) {
            Unit closestEnemy = findClosest(enemyUnits, light);
            if (closestEnemy != null)
                attack(light, closestEnemy);
        }
    }

    private class Ranged {
        public Ranged() {
            playerRanged.forEach(ranged -> {
                assignToAttack(ranged);
            });
        }

        private void assignToAttack(Unit ranged) {
            Unit closestEnemy = findClosest(enemyUnits, ranged);
            if (closestEnemy != null)
                attack(ranged, closestEnemy);
        }
    }

    private class Heavy {
        public Heavy() {
            playerHeavies.forEach(heavy -> {
                assignToAttack(heavy);
            });
        }

        private void assignToAttack(Unit heavy) {
            Unit closestEnemy = findClosest(enemyUnits, heavy);
            if (closestEnemy != null)
                attack(heavy, closestEnemy);
        }
    }

    @Override
    public PlayerAction getAction(int _player, GameState _gameState) {
        gameState = _gameState;
        physicalGameState = gameState.getPhysicalGameState();
        player = gameState.getPlayer(_player);

        board = new Board(gameState.getPhysicalGameState().getWidth(), gameState.getPhysicalGameState().getHeight());

        playerBases = new ArrayList<>();
        playerBarracks = new ArrayList<>();
        playerUnits = new ArrayList<>();
        playerWorkers = new ArrayList<>();
        playerLights = new ArrayList<>();
        playerRanged = new ArrayList<>();
        playerHeavies = new ArrayList<>();

        enemyBases = new ArrayList<>();
        enemyBarracks = new ArrayList<>();
        enemyUnits = new ArrayList<>();
        enemyWorkers = new ArrayList<>();
        enemyLights = new ArrayList<>();
        enemyRanged = new ArrayList<>();
        enemyHeavies = new ArrayList<>();

        resources = new ArrayList<>();

        for (Unit u : gameState.getPhysicalGameState().getUnits()) {
            if (u.getPlayer() == _player) {
                playerUnits.add(u);
                assignUnit(u, playerBases, playerBarracks, playerWorkers, playerLights, playerRanged, playerHeavies);
            } else if (u.getPlayer() >= 0) {
                enemyUnits.add(u);
                assignUnit(u, enemyBases, enemyBarracks, enemyWorkers, enemyLights, enemyRanged, enemyHeavies);
            } else {
                resources.add(u);
            }
        }

        basesIterator = playerBases.iterator();
        barracksIterator = playerBarracks.iterator();
        workersIterator = playerWorkers.iterator();
        lightsIterator = playerLights.iterator();
        rangedIterator = playerRanged.iterator();
        heaviesIterator = playerHeavies.iterator();

        new Base();
        new Barracks();
        new Workers();
        new Light();
        new Ranged();
        new Heavy();

        return translateActions(_player, gameState);
    }

    private void assignUnit(Unit u, List<Unit> bases, List<Unit> barracks, List<Unit> workers, List<Unit> lights,
            List<Unit> ranged, List<Unit> heavies) {
        if (u.getType() == baseType) {
            bases.add(u);
        } else if (u.getType() == barracksType) {
            barracks.add(u);
        } else if (u.getType() == workerType) {
            workers.add(u);
        } else if (u.getType() == lightType) {
            lights.add(u);
        } else if (u.getType() == rangedType) {
            ranged.add(u);
        } else if (u.getType() == heavyType) {
            heavies.add(u);
        }
    }

    private Unit findClosest(List<Unit> units, Unit reference) {
        return units.stream().min(Comparator.comparingInt(u -> distance(u, reference))).orElse(null);
    }

    private List<Unit> findUnitsWithin(List<Unit> units, Unit reference, int distance) {
        return units.stream().filter(u -> distance(u, reference) <= distance).collect(Collectors.toList());
    }

    private Unit findClosestWithin(List<Unit> units, Unit reference, int distance) {
        return findUnitsWithin(units, reference, distance).stream()
                .min(Comparator.comparingInt(u -> distance(u, reference)))
                .orElse(null);
    }

    private Set<String> findAdjacentCells(List<Unit> resources) {
        Set<String> cells = new HashSet<>();
        int[] dx = { -1, 1, 0, 0 };
        int[] dy = { 0, 0, -1, 1 };

        for (Unit resource : resources) {
            for (int i = 0; i < 4; i++) {
                int nx = resource.getX() + dx[i];
                int ny = resource.getY() + dy[i];

                String cellId = nx + "," + ny;

                if (nx >= 0 && nx < board.x &&
                        ny >= 0 && ny < board.y) {
                    cells.add(cellId);
                }
            }
        }

        return cells;
    }

    private int distance(Unit u1, Unit u2) {
        return Math.abs(u1.getX() - u2.getX()) + Math.abs(u1.getY() - u2.getY());
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        return List.of(new ParameterSpecification("PathFinding", PathFinding.class, pf));
    }

    public MyBot(UnitTypeTable unitTypeTable) {
        this(unitTypeTable, new AStarPathFinding());
    }

    public MyBot(UnitTypeTable unitTypeTable, PathFinding pathFinding) {
        super(pathFinding);
        setUnitTypes(unitTypeTable);
    }

    @Override
    public void reset(UnitTypeTable unitTypeTable) {
        setUnitTypes(unitTypeTable);
    }

    private void setUnitTypes(UnitTypeTable unitTypeTable) {
        this.unitTypeTable = unitTypeTable;
        baseType = unitTypeTable.getUnitType("Base");
        barracksType = unitTypeTable.getUnitType("Barracks");
        workerType = unitTypeTable.getUnitType("Worker");
        lightType = unitTypeTable.getUnitType("Light");
        rangedType = unitTypeTable.getUnitType("Ranged");
        heavyType = unitTypeTable.getUnitType("Heavy");
    }

    @Override
    public AI clone() {
        return new MyBot(unitTypeTable, pf);
    }

    private class Board {
        public Board(int x, int y) {
            this.x = x;
            this.y = y;
            this.size = x * y;
        }

        public int x;
        public int y;
        public int size;
    }

    private UnitTypeTable unitTypeTable;
    private UnitType baseType, barracksType, workerType, lightType, rangedType, heavyType;
    private List<Unit> playerBases, playerBarracks, playerUnits, playerWorkers, playerLights, playerRanged,
            playerHeavies;
    private List<Unit> enemyBases, enemyBarracks, enemyUnits, enemyWorkers, enemyLights, enemyRanged, enemyHeavies;
    private Iterator<Unit> basesIterator, barracksIterator, workersIterator, lightsIterator, rangedIterator,
            heaviesIterator;
    private Unit currentBase, currentBarracks, currentWorker, currentLight, currentRanged, currentHeavy;
    GameState gameState;
    PhysicalGameState physicalGameState;
    Player player;
    private List<Unit> resources;
    private Board board;
}