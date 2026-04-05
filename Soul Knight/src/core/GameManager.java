package core;

import entities.*;
import world.Dungeon;
import world.DungeonGenerator;
import input.InputHandler;
import rendering.MapRenderer;
import rendering.Texture;
import rendering.TextureLoader;
import rendering.TextRenderer;
import entities.weapons.WeaponInterface;
import entities.weapons.Weapon;
import entities.weapons.MeleeWeapon;
import entities.weapons.WeaponFactory;
import physics.CollisionManager;
import world.Tile;
import rendering.Camera;
import rendering.Sprite;
import core.GameSaveHandler;

import entities.Effect.EffectType;
import entities.Effect.PlayerEffect;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.util.Collections;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.DoubleBuffer;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class GameManager {

    private long window;
    private int width = 1280;
    private int height = 720;
    private String title = "Soul Knight LWJGL";

    private Player player;
    private Enemy enemy;
    private Enemy boss;
    private Dungeon currentDungeon;
    private MapRenderer mapRenderer;
    private InputHandler inputHandler;
    private CollisionManager collisionManager;
    private Camera camera;
    private HUD hud;

    private WeaponFactory weaponFactory;

    private AbilitySelectionScreen abilitySelectionScreen;
    private UpgradeChoiceScreen upgradeScreen;
    private GameOverScreen gameOverScreen;
    private GameState currentState = GameState.ABILITY_SELECTION;

    // Path debug beállítás
    private boolean showPathDebug = true;

    // Multiplayer állapot
    private boolean isMultiplayer = false;
    private boolean isHost = false;
    private MultiplayerClient multiplayerClient;
    private String serverIp = "localhost";
    private String serverPort = "5555";
    private int myPlayerId = -1;
    private Map<Integer, Player> otherPlayers = new HashMap<>();
    private Map<Integer, PlayerState> serverPlayerStates = new HashMap<>();
    private Set<String> processedGateEvents = new HashSet<>();
    private final Set<String> pendingGateTriggers = new LinkedHashSet<>();
    private int hostPlayerId = -1;
    private boolean hostPresent = false;
    private boolean hostAlive = true;

    // Interpolációhoz
    private float interpolationSpeed = 5.0f;

    public enum GameState {
        LOBBY,
        ABILITY_SELECTION,
        GAMEPLAY,
        UPGRADE_CHOICE,
        GAME_OVER,
        LOAD_GAME
    }

    private Texture playerIdleTexture;
    private List<Texture> walkFrames;
    private List<Texture> activeWalkFrames;
    private Map<Tile.TileType, Texture> tileTextures;
    private Texture enemyTexture;
    private Map<Integer, Texture> boxDamageTextures;
    private Map<Integer, Texture> gateAnimationTextures;

    private List<Projectile> projectiles;
    private List<Effect> effects;
    private List<PlayerEffect> playerEffects;
    private Map<Integer, List<PlayerEffect>> remotePlayerEffects;

    private Map<Integer, Effect> activeEffectsById;
    private int nextEffectId = 1;
    private Random effectRandom = new Random();
    private long currentDungeonSeed = -1L;
    private int currentLevelIndex = 1;
    private float enemyDamageMultiplier = 1.0f;
    private float enemySpeedMultiplier = 1.0f;
    private float enemyPathDeviationChance = 0.25f;
    private float enemyPathDeviationRadius = 2.0f;

    private Map<Effect.EffectType, Texture> effectTextures;

    private Texture weaponCrateTexture;
    private Texture openCrateTexture;
    private Texture emptyCrateTexture;

    private float meleeCooldownTime = 0.5f;
    private float lastMeleeAttackTime = Float.NEGATIVE_INFINITY;

    private TextRenderer textRenderer;
    private Texture fontTexture;
    private Texture teleportPadTexture;
    private Texture projectileTexture;
    private TextRenderer waitingMessageRenderer;
    private TextRenderer waitingPlayerCountRenderer;
    private String waitingPlayerCountText = "";
    private boolean allPlayersReady = false;

    private boolean bossDefeated = false;
    private Player.Ability playerAbility;
    private String playerName;
    private int saveIdToLoad = -1;
    private float lastRangedAttackTime = Float.NEGATIVE_INFINITY;
    private final float RANGED_COOLDOWN = 0.45f; // Másodperc
    private final float RANGED_PROJECTILE_SPEED = 240.0f;
    private boolean projectileSentThisFrame = false;
    private float enemySyncTimer = 0f;
    private final float ENEMY_SYNC_INTERVAL = 0.1f;

    private int frameCounter = 0;
    private Map<Integer, Projectile> syncedProjectiles = new HashMap<>();
    private Map<Integer, Float> lastEnemyPositions = new HashMap<>();
    private static final int MULTIPLAYER_MAX_PLAYERS = 2;

    // Setter a path debug beállításhoz
    public void setShowPathDebug(boolean showPathDebug) {
        this.showPathDebug = showPathDebug;
    }

    private void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            width = pWidth.get(0);
            height = pHeight.get(0);
        }

        inputHandler = new InputHandler(window);

        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(
                window,
                (vidmode.width() - width) / 2,
                (vidmode.height() - height) / 2
        );

        glfwMakeContextCurrent(window);
        GL.createCapabilities();

        glfwSwapInterval(1);

        glfwShowWindow(window);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);

        camera = new Camera(width, height);
        hud = new HUD(width, height);

        weaponFactory = new WeaponFactory();
        weaponFactory.loadWeaponSprites();

        abilitySelectionScreen = new AbilitySelectionScreen(window, width, height);
        upgradeScreen = new UpgradeChoiceScreen(window, width, height, player);
        
        waitingMessageRenderer = new TextRenderer(
                "Várakozás a többi játékos csatlakozására...",
                new java.awt.Font("Arial", java.awt.Font.BOLD, 34),
                java.awt.Color.WHITE
        );
        updateWaitingPlayerCountText();
    }

    private void initGameplay(String playerName, Player.Ability ability) {
        this.playerName = playerName;
        this.playerAbility = ability;

        this.currentLevelIndex = 1;
        this.enemyDamageMultiplier = 1.0f;
        this.enemySpeedMultiplier = 1.0f;
        this.enemyPathDeviationChance = 0.25f;
        this.enemyPathDeviationRadius = 2.0f;
        this.nextEffectId = 1;
        this.activeEffectsById = new HashMap<>();
        this.currentDungeonSeed = System.nanoTime();
        this.effectRandom = new Random(currentDungeonSeed ^ 0xBEEFL);

        this.enemy = new Enemy(0, 0, 0, 0, null, 0, null, null, null, null);
        this.boss = new Boss(0, 0, 0, 0, null, 0, null, null, null, null);

        playerIdleTexture = TextureLoader.loadTexture("character1.png");
        if (playerIdleTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a character1.png textúrát.");
        }

        walkFrames = new ArrayList<>();
        Texture walk1 = TextureLoader.loadTexture("player_walk1.png");
        Texture walk2 = TextureLoader.loadTexture("player_walk2.png");
        Texture walk3 = TextureLoader.loadTexture("player_walk3.png");
        if (walk1 != null) walkFrames.add(walk1);
        else System.err.println("HIBA: Nem sikerült betölteni a player_walk1.png textúrát.");
        if (walk2 != null) walkFrames.add(walk2);
        else System.err.println("HIBA: Nem sikerült betölteni a player_walk2.png textúrát.");
        if (walk3 != null) walkFrames.add(walk3);
        else System.err.println("HIBA: Nem sikerült betölteni a player_walk3.png textúrát.");
        Sprite walkSprite = walkFrames.isEmpty() ? null : new Sprite(walkFrames, 0.1f, true);
        
        projectileTexture = TextureLoader.loadTexture("projectile.png");
        if (projectileTexture == null) {
            projectileTexture = TextureLoader.loadTexture("ranged_attack1.png");
            if (projectileTexture == null) {
                System.err.println("HIBA: Nem sikerült betölteni a projectile.png vagy ranged_attack1.png textúrát.");
            }
        }

        activeWalkFrames = new ArrayList<>();
        String abilityTextureName;
        switch (ability) {
            case SPEED:
                abilityTextureName = "Speed.png";
                break;
            case DODGE:
                abilityTextureName = "Dodge.png";
                break;
            case BLOCK:
                abilityTextureName = "Block.png";
                break;
            default:
                abilityTextureName = null;
        }
        if (abilityTextureName != null) {
            Texture abilityTexture = TextureLoader.loadTexture(abilityTextureName);
            if (abilityTexture != null) {
                activeWalkFrames.add(abilityTexture);
            } else {
                System.err.println("HIBA: Nem sikerült betölteni a " + abilityTextureName + " textúrát.");
            }
        }
        Sprite activeWalkSprite = activeWalkFrames.isEmpty() ? null : new Sprite(activeWalkFrames, 0.1f, false);

        tileTextures = new HashMap<>();
        tileTextures.put(Tile.TileType.FLOOR, TextureLoader.loadTexture("grass.png"));
        if (tileTextures.get(Tile.TileType.FLOOR) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a grass.png textúrát.");
        }
        tileTextures.put(Tile.TileType.WALL, TextureLoader.loadTexture("wall_tile.png"));
        if (tileTextures.get(Tile.TileType.WALL) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a wall_tile.png textúrát.");
        }
        tileTextures.put(Tile.TileType.GATE, TextureLoader.loadTexture("gate_texture.png"));
        if (tileTextures.get(Tile.TileType.GATE) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a gate_texture.png textúrát.");
        }

        weaponCrateTexture = TextureLoader.loadTexture("weapon_crate_full.png");
        if (weaponCrateTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a weapon_crate_full.png textúrát.");
        }
        openCrateTexture = TextureLoader.loadTexture("weapon_crate_open.png");
        if (openCrateTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a weapon_crate_open.png textúrát.");
        }
        emptyCrateTexture = TextureLoader.loadTexture("weapon_crate_empty.png");
        if (emptyCrateTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a weapon_crate_empty.png textúrát.");
        }
        tileTextures.put(Tile.TileType.WEAPON_CRATE, weaponCrateTexture);

        Texture shopFloorTexture = TextureLoader.loadTexture("SHOP_FLOOR.png");
        if (shopFloorTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a SHOP_FLOOR.png textúrát.");
        }
        tileTextures.put(Tile.TileType.SHOP_FLOOR, shopFloorTexture);

        Texture boxTexture = TextureLoader.loadTexture("box.png");
        if (boxTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a box.png textúrát.");
        }
        tileTextures.put(Tile.TileType.BOX, boxTexture);

        boxDamageTextures = new HashMap<>();
        boxDamageTextures.put(1, TextureLoader.loadTexture("box_cracked1.png"));
        if (boxDamageTextures.get(1) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a box_cracked1.png textúrát.");
        }
        boxDamageTextures.put(2, TextureLoader.loadTexture("box_cracked2.png"));
        if (boxDamageTextures.get(2) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a box_cracked2.png textúrát.");
        }

        gateAnimationTextures = new HashMap<>();
        gateAnimationTextures.put(0, TextureLoader.loadTexture("gate_anim_01.png"));
        if (gateAnimationTextures.get(0) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a gate_anim_01.png textúrát.");
        }
        gateAnimationTextures.put(1, TextureLoader.loadTexture("gate_anim_02.png"));
        if (gateAnimationTextures.get(1) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a gate_anim_02.png textúrát.");
        }

        enemyTexture = TextureLoader.loadTexture("enemy1.png");
        if (enemyTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a enemy1.png textúrát.");
        }

        effectTextures = new HashMap<>();
        effectTextures.put(Effect.EffectType.SPEED_BOOST, TextureLoader.loadTexture("speed_boost.png"));
        if (effectTextures.get(Effect.EffectType.SPEED_BOOST) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a speed_boost.png textúrát.");
        }
        effectTextures.put(Effect.EffectType.DAMAGE_BOOST, TextureLoader.loadTexture("damage_boost.png"));
        if (effectTextures.get(Effect.EffectType.DAMAGE_BOOST) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a damage_boost.png textúrát.");
        }
        effectTextures.put(Effect.EffectType.HEALTH_REGEN, TextureLoader.loadTexture("health_regen.png"));
        if (effectTextures.get(Effect.EffectType.HEALTH_REGEN) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a health_regen.png textúrát.");
        }

        teleportPadTexture = TextureLoader.loadTexture("teleport_pad.png");
        if (teleportPadTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a teleport_pad.png textúrát.");
        }
        tileTextures.put(Tile.TileType.TELEPORT_PAD, teleportPadTexture);

        fontTexture = TextureLoader.loadTexture("font.png");
        if (fontTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a font.png textúrát.");
        }
        textRenderer = new TextRenderer("CRIT", new java.awt.Font("Arial", java.awt.Font.BOLD, 48), java.awt.Color.WHITE);

        currentDungeon = DungeonGenerator.generateRandomDungeonWithSeed(
                currentDungeonSeed,
                32,
                tileTextures,
                enemyTexture,
                boxDamageTextures,
                gateAnimationTextures,
                weaponCrateTexture,
                openCrateTexture,
                emptyCrateTexture,
                weaponFactory,
                textRenderer,
                effectTextures,
                teleportPadTexture
        );

        player = new Player(
                currentDungeon.getPlayerSpawnX(),
                currentDungeon.getPlayerSpawnY(),
                50, 50, playerIdleTexture, window, weaponFactory, tileTextures.get(Tile.TileType.FLOOR),
                textRenderer
        );

        player.activateSpawnProtection();

        applyDifficultyToEnemies();

        setupPlayerDamageListener(player);
        setupPlayerGateListener(player);

        for (Enemy enemy : currentDungeon.getEnemies()) {
            enemy.setTargetPlayer(this.player);
            enemy.setShowPathDebug(this.showPathDebug);
        }

        player.setDungeon(currentDungeon);
        player.setSprites(walkSprite);
        player.setActiveSprites(activeWalkSprite);
        player.setWeapon(weaponFactory.createWeapon("pistol"), "pistol");
        player.setAbility(ability);
        player.setName(playerName);

        int dungeonWidthPixels = currentDungeon.getWidthTiles() * currentDungeon.getTileSize();
        int dungeonHeightPixels = currentDungeon.getHeightTiles() * currentDungeon.getTileSize();
        camera.follow(player, dungeonWidthPixels, dungeonHeightPixels);

        mapRenderer = new MapRenderer();
        collisionManager = new CollisionManager(currentDungeon);

        projectiles = new ArrayList<>();
        effects = new ArrayList<>();
        playerEffects = new ArrayList<>();
        remotePlayerEffects = new HashMap<>();

        currentState = GameState.GAMEPLAY;
        glfwSetCharCallback(window, null);
        glfwSetKeyCallback(window, inputHandler.getKeyCallback());
        glfwSetMouseButtonCallback(window, inputHandler.getMouseButtonCallback());

        glEnable(GL_TEXTURE_2D);

        for (Enemy enemy : currentDungeon.getEnemies()) {
            if (enemy instanceof Boss) {
                boss = enemy.clone();
                for (Enemy normalEnemy : currentDungeon.getEnemies()) {
                    if (!(normalEnemy instanceof Boss)) {
                        this.enemy = normalEnemy.clone();
                        break;
                    }
                }
            }
        }

        upgradeScreen.setPlayer(player);
        upgradeScreen.setDungeon(enemy, boss);
    }

    private void generateAndSendDungeon() {
        if (!isHost) return;

        if (currentState == GameState.GAMEPLAY) {
            System.out.println("⚠️  MÁR GAMEPLAY STATE-BEN VAGYOK - NEM GENERÁLOK ÚJ DUNGEON-T");
            return;
        }

        //System.out.println("🏠 HOST – DUNGEON GENERÁLÁS ÉS KÜLDÉSE...");

        long seed = System.currentTimeMillis();

        // ✨ JAVÍTÁS: Host is csak egyszer inicializáljon
        if (currentState != GameState.GAMEPLAY) {
            initGameplayMultiplayer(playerName, playerAbility);
        }

        generateDungeonWithSeed(seed);

        if (player == null) {
            initPlayerForMultiplayer();
        }

        currentState = GameState.GAMEPLAY;

        // ✨ FONTOS: Küldd el a seed-et MINDENKINEK
        String message = "DUNGEON_SEED:" + seed;
        multiplayerClient.sendTCPMessage("BROADCAST:" + message);
        //System.out.println("🌱 DUNGEON SEED KÜLDVE MINDENKINEK: " + seed);
    }

    // ✨ ÚJ: Textúrák betöltése multiplayer számára
    private void initGameplayResources() {
        //System.out.println("🔄 Initializing multiplayer gameplay resources...");

        // ✨ JAVÍTOTT: Ne töröljünk, csak inicializáljunk ha null
        if (tileTextures == null) {
            tileTextures = new HashMap<>();
        }

        // ✨ JAVÍTOTT: Csak akkor töltsünk be, ha még nincs betöltve
        loadTextures();

        // Player textúrák
        if (playerIdleTexture == null) {
            playerIdleTexture = TextureLoader.loadTexture("character1.png");
            if (playerIdleTexture != null) {
                System.out.println("✅ Loaded character1.png");
            } else {
                System.err.println("❌ Failed to load character1.png");
            }
        }

        // Walk frames
        if (walkFrames == null || walkFrames.isEmpty()) {
            walkFrames = new ArrayList<>();
            Texture walk1 = TextureLoader.loadTexture("player_walk1.png");
            Texture walk2 = TextureLoader.loadTexture("player_walk2.png");
            Texture walk3 = TextureLoader.loadTexture("player_walk3.png");
            if (walk1 != null) walkFrames.add(walk1);
            else System.err.println("❌ Failed to load player_walk1.png");
            if (walk2 != null) walkFrames.add(walk2);
            else System.err.println("❌ Failed to load player_walk2.png");
            if (walk3 != null) walkFrames.add(walk3);
            else System.err.println("❌ Failed to load player_walk3.png");
            System.out.println("✅ Loaded " + walkFrames.size() + " walk frames");
        }

        // Ability frames
        if (activeWalkFrames == null || activeWalkFrames.isEmpty()) {
            activeWalkFrames = new ArrayList<>();
            String abilityTextureName;
            switch (playerAbility) {
                case SPEED:
                    abilityTextureName = "Speed.png";
                    break;
                case DODGE:
                    abilityTextureName = "Dodge.png";
                    break;
                case BLOCK:
                    abilityTextureName = "Block.png";
                    break;
                default:
                    abilityTextureName = null;
            }
            if (abilityTextureName != null) {
                Texture abilityTexture = TextureLoader.loadTexture(abilityTextureName);
                if (abilityTexture != null) {
                    activeWalkFrames.add(abilityTexture);
                    System.out.println("✅ Loaded " + abilityTextureName);
                } else {
                    System.err.println("❌ Failed to load " + abilityTextureName);
                }
            }
        }

        // Enemy texture
        if (enemyTexture == null) {
            enemyTexture = TextureLoader.loadTexture("enemy1.png");
            if (enemyTexture != null) {
                System.out.println("✅ Loaded enemy1.png");
            } else {
                System.err.println("❌ Failed to load enemy1.png");
            }
        }

        // Effect textures
        if (effectTextures == null || effectTextures.isEmpty()) {
            effectTextures = new HashMap<>();
            effectTextures.put(Effect.EffectType.SPEED_BOOST, TextureLoader.loadTexture("speed_boost.png"));
            effectTextures.put(Effect.EffectType.DAMAGE_BOOST, TextureLoader.loadTexture("damage_boost.png"));
            effectTextures.put(Effect.EffectType.HEALTH_REGEN, TextureLoader.loadTexture("health_regen.png"));
            System.out.println("✅ Loaded " + effectTextures.size() + " effect textures");
        }

        // Special textures
        if (weaponCrateTexture == null) {
            weaponCrateTexture = TextureLoader.loadTexture("weapon_crate_full.png");
            if (weaponCrateTexture != null) {
                System.out.println("✅ Loaded weapon_crate_full.png");
            }
        }

        if (openCrateTexture == null) {
            openCrateTexture = TextureLoader.loadTexture("weapon_crate_open.png");
        }

        if (emptyCrateTexture == null) {
            emptyCrateTexture = TextureLoader.loadTexture("weapon_crate_empty.png");
        }

        if (teleportPadTexture == null) {
            teleportPadTexture = TextureLoader.loadTexture("teleport_pad.png");
            if (teleportPadTexture != null) {
                tileTextures.put(Tile.TileType.TELEPORT_PAD, teleportPadTexture);
            }
        }

        // Font texture
        if (fontTexture == null) {
            fontTexture = TextureLoader.loadTexture("font.png");
            if (fontTexture != null) {
                System.out.println("✅ Loaded font.png");
            }
        }

        // Text renderer
        if (textRenderer == null) {
            textRenderer = new TextRenderer("CRIT", new java.awt.Font("Arial", java.awt.Font.BOLD, 48), java.awt.Color.WHITE);
        }

        // Box damage textures
        if (boxDamageTextures == null || boxDamageTextures.isEmpty()) {
            boxDamageTextures = new HashMap<>();
            boxDamageTextures.put(1, TextureLoader.loadTexture("box_cracked1.png"));
            boxDamageTextures.put(2, TextureLoader.loadTexture("box_cracked2.png"));
        }

        // Gate animation textures
        if (gateAnimationTextures == null || gateAnimationTextures.isEmpty()) {
            gateAnimationTextures = new HashMap<>();
            gateAnimationTextures.put(0, TextureLoader.loadTexture("gate_anim_01.png"));
            gateAnimationTextures.put(1, TextureLoader.loadTexture("gate_anim_02.png"));
        }

        // Inicializáld a listákat
        if (projectiles == null) {
            projectiles = new ArrayList<>();
        }
        if (effects == null) {
            effects = new ArrayList<>();
        }
        if (playerEffects == null) {
            playerEffects = new ArrayList<>();
        }
        if (remotePlayerEffects == null) {
            remotePlayerEffects = new HashMap<>();
        }

        System.out.println("✅ Multiplayer gameplay resources initialized - " +
                tileTextures.size() + " tile textures, " +
                (walkFrames != null ? walkFrames.size() : 0) + " walk frames");
    }

    // ✨ ÚJ: Segédmetódus a textúrák betöltéséhez
    private void loadTextures() {
        if (tileTextures.isEmpty()) {
            System.out.println("📥 Loading tile textures...");

            // Alap tile-ok
            Texture floorTexture = TextureLoader.loadTexture("grass.png");
            if (floorTexture != null) {
                tileTextures.put(Tile.TileType.FLOOR, floorTexture);
                System.out.println("✅ Loaded grass.png");
            } else {
                System.err.println("❌ Failed to load grass.png");
            }

            Texture wallTexture = TextureLoader.loadTexture("wall_tile.png");
            if (wallTexture != null) {
                tileTextures.put(Tile.TileType.WALL, wallTexture);
                System.out.println("✅ Loaded wall_tile.png");
            } else {
                System.err.println("❌ Failed to load wall_tile.png");
            }

            Texture gateTexture = TextureLoader.loadTexture("gate_texture.png");
            if (gateTexture != null) {
                tileTextures.put(Tile.TileType.GATE, gateTexture);
                System.out.println("✅ Loaded gate_texture.png");
            } else {
                System.err.println("❌ Failed to load gate_texture.png");
            }

            // Speciális tile-ok
            Texture shopFloorTexture = TextureLoader.loadTexture("SHOP_FLOOR.png");
            if (shopFloorTexture != null) {
                tileTextures.put(Tile.TileType.SHOP_FLOOR, shopFloorTexture);
                System.out.println("✅ Loaded SHOP_FLOOR.png");
            }

            Texture boxTexture = TextureLoader.loadTexture("box.png");
            if (boxTexture != null) {
                tileTextures.put(Tile.TileType.BOX, boxTexture);
                System.out.println("✅ Loaded box.png");
            }

            // Weapon crate
            if (weaponCrateTexture != null) {
                tileTextures.put(Tile.TileType.WEAPON_CRATE, weaponCrateTexture);
            }

            System.out.println("📦 Total tile textures loaded: " + tileTextures.size());
        }
    }

    public void initMultiplayer(String serverIp, String serverPort, boolean isHost) {
        this.isMultiplayer = true;
        this.isHost = isHost;
        this.serverIp = serverIp;
        this.serverPort = serverPort;

        try {
            System.out.println("🎯 MULTIPLAYER INICIALIZÁLÁS");
            System.out.println("📍 Cél: " + serverIp + ":" + serverPort);

            multiplayerClient = new MultiplayerClient(serverIp, Integer.parseInt(serverPort));
            multiplayerClient.connect();

            if (multiplayerClient.isConnected()) {
                System.out.println("✅ MULTIPLAYER KAPCSOLAT LÉTREJÖTT");

                // ✨ FONTOS: Várj a PLAYER_ID-re
                boolean success = waitForPlayerId();

                if (success) {
                    System.out.println("🎮 JÁTÉKOS ID MEGÉRKEZETT: " + myPlayerId);

                    // ✨ JAVÍTÁS: KÜLDJÜK EL A JOIN_GAME ÜZENETET!
                    String joinMessage = "JOIN_GAME:" +
                            (playerName != null ? playerName : "Player") + ":" +
                            (playerAbility != null ? playerAbility.name() : "SPEED") + ":" +
                            showPathDebug;

                    multiplayerClient.sendTCPMessage(joinMessage);
                    System.out.println("📤 JOIN_GAME elküldve a szervernek: " + joinMessage);

                } else {
                    System.err.println("❌ Nem sikerült Player ID-t kapni");
                }
            }

        } catch (Exception e) {
            System.err.println("❌ MULTIPLAYER HIBA: " + e.getMessage());
            currentState = GameState.LOBBY;
        }
    }

    private boolean waitForPlayerId() {
        try {
            //System.out.println("⏳ Waiting for player ID from server...");

            if (multiplayerClient != null && multiplayerClient.isReadyForGame()) {
                myPlayerId = multiplayerClient.getPlayerId();
                return true;
            }

            for (int i = 0; i < 50; i++) { // 5 másodperc
                List<String> messages = multiplayerClient.getReceivedMessages();
                for (String msg : messages) {
                    System.out.println("📨 FROM SERVER: " + msg);
                    if (msg.startsWith("PLAYER_ID:")) {
                        myPlayerId = Integer.parseInt(msg.substring(10));
                        multiplayerClient.setPlayerId(myPlayerId);
                        multiplayerClient.registerUDP();
                        //System.out.println("✅ Player ID received: " + myPlayerId);
                        return true;
                    }
                }
                Thread.sleep(100);
            }

            System.err.println("❌ Timeout waiting for player ID");
            return false;
        } catch (Exception e) {
            System.err.println("❌ Error waiting for player ID: " + e.getMessage());
            return false;
        }
    }

    public void startMultiplayerGame(String playerName, String ability, String serverIp, String serverPort, boolean isHost) {
        //System.out.println("🎯 GameManager.startMultiplayerGame() called - CSAK EGYSZER KELL HÍVÓDNIA!");

        // ✨ VÉDELEM: Ellenőrizzük, hogy nem fut-e már
        if (this.isMultiplayer) {
            System.out.println("⚠️  MÁR MULTIPLAYER MÓDBAN VAN - NEM INDÍTHATÓ ÚJRA!");
            return;
        }

        this.playerName = playerName;
        this.playerAbility = Player.Ability.valueOf(ability);
        this.isMultiplayer = true;
        this.isHost = isHost;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.hostPlayerId = isHost ? myPlayerId : -1;
        this.hostPresent = isHost;
        this.hostAlive = true;

        if (isHost) {
            this.showPathDebug = false;
        }

        this.isMultiplayer = true;
        this.isHost = isHost;
        this.serverIp = serverIp;
        this.serverPort = serverPort;

        // Multiplayerben a választás már a Lobby-ban megtörtént,
        // ezért itt nem jelenítjük meg újra az ability screen-t.
        this.currentState = GameState.LOBBY;

        // Inicializáljuk a multiplayer kapcsolatot
        initMultiplayer(serverIp, serverPort, isHost);

        // Beállítjuk a játékos adatait
        this.playerName = playerName;
        this.playerAbility = Player.Ability.valueOf(ability);

        //System.out.println("✅ Multiplayer game setup complete - waiting for server...");
    }

    private void initGameplayMultiplayer(String playerName, Player.Ability ability) {
        //System.out.println("🎮 MULTIPLAYER GAMEPLAY INICIALIZÁLÁS - UGYANAZ, MINT SINGLEPLAYERBEN");

        // ✨ FONTOS: Minden ugyanaz, mint singleplayerben
        this.playerName = playerName;
        this.playerAbility = ability;

        this.currentLevelIndex = 1;
        this.enemyDamageMultiplier = 1.0f;
        this.enemySpeedMultiplier = 1.0f;
        this.enemyPathDeviationChance = 0.25f;
        this.enemyPathDeviationRadius = 2.0f;
        this.nextEffectId = 1;
        this.activeEffectsById = new HashMap<>();
        this.effectRandom = new Random();

        // Alap inicializáció - UGYANAZ
        this.enemy = new Enemy(0, 0, 0, 0, null, 0, null, null, null, null);
        this.boss = new Boss(0, 0, 0, 0, null, 0, null, null, null, null);

        // Textúrák betöltése - UGYANAZ
        playerIdleTexture = TextureLoader.loadTexture("character1.png");
        if (playerIdleTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a character1.png textúrát.");
        }

        // Walk frames - UGYANAZ
        walkFrames = new ArrayList<>();
        Texture walk1 = TextureLoader.loadTexture("player_walk1.png");
        Texture walk2 = TextureLoader.loadTexture("player_walk2.png");
        Texture walk3 = TextureLoader.loadTexture("player_walk3.png");
        if (walk1 != null) walkFrames.add(walk1);
        if (walk2 != null) walkFrames.add(walk2);
        if (walk3 != null) walkFrames.add(walk3);
        Sprite walkSprite = walkFrames.isEmpty() ? null : new Sprite(walkFrames, 0.1f, true);
        
        projectileTexture = TextureLoader.loadTexture("projectile.png");
        if (projectileTexture == null) {
            projectileTexture = TextureLoader.loadTexture("ranged_attack1.png");
            if (projectileTexture == null) {
                System.err.println("HIBA: Nem sikerült betölteni a projectile.png vagy ranged_attack1.png textúrát.");
            }
        }

        activeWalkFrames = new ArrayList<>();
        String abilityTextureName;
        switch (ability) {
            case SPEED:
                abilityTextureName = "Speed.png";
                break;
            case DODGE:
                abilityTextureName = "Dodge.png";
                break;
            case BLOCK:
                abilityTextureName = "Block.png";
                break;
            default:
                abilityTextureName = null;
        }
        if (abilityTextureName != null) {
            Texture abilityTexture = TextureLoader.loadTexture(abilityTextureName);
            if (abilityTexture != null) {
                activeWalkFrames.add(abilityTexture);
            }
        }
        Sprite activeWalkSprite = activeWalkFrames.isEmpty() ? null : new Sprite(activeWalkFrames, 0.1f, false);

        // Tile textúrák - UGYANAZ
        tileTextures = new HashMap<>();
        tileTextures.put(Tile.TileType.FLOOR, TextureLoader.loadTexture("grass.png"));
        tileTextures.put(Tile.TileType.WALL, TextureLoader.loadTexture("wall_tile.png"));
        tileTextures.put(Tile.TileType.GATE, TextureLoader.loadTexture("gate_texture.png"));

        weaponCrateTexture = TextureLoader.loadTexture("weapon_crate_full.png");
        openCrateTexture = TextureLoader.loadTexture("weapon_crate_open.png");
        emptyCrateTexture = TextureLoader.loadTexture("weapon_crate_empty.png");
        tileTextures.put(Tile.TileType.WEAPON_CRATE, weaponCrateTexture);

        Texture shopFloorTexture = TextureLoader.loadTexture("SHOP_FLOOR.png");
        if (shopFloorTexture != null) {
            tileTextures.put(Tile.TileType.SHOP_FLOOR, shopFloorTexture);
        }

        Texture boxTexture = TextureLoader.loadTexture("box.png");
        if (boxTexture != null) {
            tileTextures.put(Tile.TileType.BOX, boxTexture);
        }

        boxDamageTextures = new HashMap<>();
        boxDamageTextures.put(1, TextureLoader.loadTexture("box_cracked1.png"));
        boxDamageTextures.put(2, TextureLoader.loadTexture("box_cracked2.png"));

        gateAnimationTextures = new HashMap<>();
        gateAnimationTextures.put(0, TextureLoader.loadTexture("gate_anim_01.png"));
        gateAnimationTextures.put(1, TextureLoader.loadTexture("gate_anim_02.png"));

        enemyTexture = TextureLoader.loadTexture("enemy1.png");

        effectTextures = new HashMap<>();
        effectTextures.put(Effect.EffectType.SPEED_BOOST, TextureLoader.loadTexture("speed_boost.png"));
        effectTextures.put(Effect.EffectType.DAMAGE_BOOST, TextureLoader.loadTexture("damage_boost.png"));
        effectTextures.put(Effect.EffectType.HEALTH_REGEN, TextureLoader.loadTexture("health_regen.png"));

        teleportPadTexture = TextureLoader.loadTexture("teleport_pad.png");
        if (teleportPadTexture != null) {
            tileTextures.put(Tile.TileType.TELEPORT_PAD, teleportPadTexture);
        }

        fontTexture = TextureLoader.loadTexture("font.png");
        textRenderer = new TextRenderer("CRIT", new java.awt.Font("Arial", java.awt.Font.BOLD, 48), java.awt.Color.WHITE);

        // ✨ JAVÍTÁS: NE generálj dungeon-t itt!
        // currentDungeon = DungeonGenerator.generateRandomDungeon(...); // ← EZT TÁVOLÍTSD EL!

        // PLAYER LÉTREHOZÁS - DE CSAK ALAP PLAYER, MÉG NINCS DUNGEON!
        player = new Player(
                currentDungeon != null ? currentDungeon.getPlayerSpawnX() : 100, // ✨ Ha van dungeon, használjuk a spawn-t
                currentDungeon != null ? currentDungeon.getPlayerSpawnY() : 100,
                50, 50, playerIdleTexture, window, weaponFactory, tileTextures.get(Tile.TileType.FLOOR),
                textRenderer
        );

        player.activateSpawnProtection();

        setupPlayerDamageListener(player);
        setupPlayerGateListener(player);

        // ✨ JAVÍTÁS: Enemy beállításokat KÉSŐBB, amikor már van dungeon
        // for (Enemy enemy : currentDungeon.getEnemies()) { // ← EZT TÁVOLÍTSD EL!
        //     enemy.setTargetPlayer(this.player);
        //     enemy.setShowPathDebug(this.showPathDebug);
        // }

        player.setDungeon(null); // ✨ Még nincs dungeon
        player.setSprites(walkSprite);
        player.setActiveSprites(activeWalkSprite);
        player.setWeapon(weaponFactory.createWeapon("pistol"), "pistol");
        player.setAbility(ability);
        player.setName(playerName);

        // Kamera - UGYANAZ
        camera.follow(player, width, height); // ✨ Alap kamera, mert nincs dungeon

        // Renderer és collision - DE collisionManager-t KÉSŐBB
        mapRenderer = new MapRenderer();
        // collisionManager = new CollisionManager(currentDungeon); // ← EZT TÁVOLÍTSD EL!

        projectiles = new ArrayList<>();
        effects = new ArrayList<>();
        playerEffects = new ArrayList<>();
        remotePlayerEffects = new HashMap<>();

        // ✨ FONTOS: Állapot beállítása - DE Client maradjon ABILITY_SELECTION-ben
        // currentState = GameState.GAMEPLAY; // ← EZT TÁVOLÍTSD EL!

        // Input handler - UGYANAZ
        glfwSetCharCallback(window, null);
        glfwSetKeyCallback(window, inputHandler.getKeyCallback());
        glfwSetMouseButtonCallback(window, inputHandler.getMouseButtonCallback());

        glEnable(GL_TEXTURE_2D);

        // ✨ JAVÍTÁS: Boss és enemy referenciák KÉSŐBB, amikor már van dungeon
        // for (Enemy enemy : currentDungeon.getEnemies()) { // ← EZT TÁVOLÍTSD EL!
        //     if (enemy instanceof Boss) {
        //         boss = enemy.clone();
        //         for (Enemy normalEnemy : currentDungeon.getEnemies()) {
        //             if (!(normalEnemy instanceof Boss)) {
        //                 this.enemy = normalEnemy.clone();
        //                 break;
        //             }
        //         }
        //     }
        // }

        upgradeScreen.setPlayer(player);
        // upgradeScreen.setDungeon(enemy, boss); // ← EZT TÁVOLÍTSD EL!

//        System.out.println("✅ MULTIPLAYER GAMEPLAY INICIALIZÁLVA - DUNGEON NÉLKÜL");
//        System.out.println("   Player: " + playerName + " (" + ability + ")");
//        System.out.println("   Dungeon: MÉG NEM GENERÁLT");
    }

    private void createBasicDungeonForMultiplayer() {
        //System.out.println("🎯 Creating multiplayer dungeon...");

        // ✨ JAVÍTOTT: Ne próbálj player-t használni itt!
        currentDungeon = DungeonGenerator.generateRandomDungeon(
                32, // ✨ Ugyanaz a méret mint singleplayerben
                tileTextures,
                enemyTexture,
                boxDamageTextures,
                gateAnimationTextures,
                weaponCrateTexture,
                openCrateTexture,
                emptyCrateTexture,
                weaponFactory,
                textRenderer,
                effectTextures,
                teleportPadTexture
        );

        if (currentDungeon == null) {
            System.err.println("❌ FAILED TO GENERATE DUNGEON!");
            return;
        }

        //System.out.println("✅ Multiplayer dungeon generated - Tiles: " +
//                currentDungeon.getWidthTiles() + "x" + currentDungeon.getHeightTiles() +
//                ", Spawn: " + currentDungeon.getPlayerSpawnX() + "," + currentDungeon.getPlayerSpawnY());
    }

    private void waitForServerDungeonData() {
        //System.out.println("⏳ Waiting for server dungeon data...");

        // Itt később fogjuk kezelni a processServerMessages-ben
    }

    private void loadNextLevel() {
        advanceDifficultyScaling();
        cleanupForNextLevel();
        projectiles.clear();
        if (effects != null) {
            effects.clear();
        } else {
            effects = new ArrayList<>();
        }
        if (playerEffects != null) {
            playerEffects.clear();
        } else {
            playerEffects = new ArrayList<>();
        }
        if (remotePlayerEffects != null) {
            remotePlayerEffects.clear();
        } else {
            remotePlayerEffects = new HashMap<>();
        }
        processedGateEvents.clear();
        pendingGateTriggers.clear();

        if (activeEffectsById == null) {
            activeEffectsById = new HashMap<>();
        } else {
            activeEffectsById.clear();
        }
        nextEffectId = 1;

        weaponFactory.loadWeaponSprites();

        playerIdleTexture = TextureLoader.loadTexture("character1.png");
        if (playerIdleTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a character1.png textúrát.");
        }

        walkFrames = new ArrayList<>();
        Texture walk1 = TextureLoader.loadTexture("player_walk1.png");
        Texture walk2 = TextureLoader.loadTexture("player_walk2.png");
        Texture walk3 = TextureLoader.loadTexture("player_walk3.png");
        if (walk1 != null) walkFrames.add(walk1);
        else System.err.println("HIBA: Nem sikerült betölteni a player_walk1.png textúrát.");
        if (walk2 != null) walkFrames.add(walk2);
        else System.err.println("HIBA: Nem sikerült betölteni a player_walk2.png textúrát.");
        if (walk3 != null) walkFrames.add(walk3);
        else System.err.println("HIBA: Nem sikerült betölteni a player_walk3.png textúrát.");
        Sprite walkSprite = walkFrames.isEmpty() ? null : new Sprite(walkFrames, 0.1f, true);
        
        projectileTexture = TextureLoader.loadTexture("projectile.png");
        if (projectileTexture == null) {
            projectileTexture = TextureLoader.loadTexture("ranged_attack1.png");
            if (projectileTexture == null) {
                System.err.println("HIBA: Nem sikerült betölteni a projectile.png vagy ranged_attack1.png textúrát.");
            }
        }

        hud = new HUD(width, height);

        activeWalkFrames = new ArrayList<>();
        String abilityTextureName;
        switch (playerAbility) {
            case SPEED:
                abilityTextureName = "Speed.png";
                break;
            case DODGE:
                abilityTextureName = "Dodge.png";
                break;
            case BLOCK:
                abilityTextureName = "Block.png";
                break;
            default:
                abilityTextureName = null;
        }
        if (abilityTextureName != null) {
            Texture abilityTexture = TextureLoader.loadTexture(abilityTextureName);
            if (abilityTexture != null) {
                activeWalkFrames.add(abilityTexture);
            } else {
                System.err.println("HIBA: Nem sikerült betölteni a " + abilityTextureName + " textúrát.");
            }
        }
        Sprite activeWalkSprite = activeWalkFrames.isEmpty() ? null : new Sprite(activeWalkFrames, 0.1f, false);

        tileTextures = new HashMap<>();
        tileTextures.put(Tile.TileType.FLOOR, TextureLoader.loadTexture("grass.png"));
        if (tileTextures.get(Tile.TileType.FLOOR) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a grass.png textúrát.");
        }
        tileTextures.put(Tile.TileType.WALL, TextureLoader.loadTexture("wall_tile.png"));
        if (tileTextures.get(Tile.TileType.WALL) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a wall_tile.png textúrát.");
        }
        tileTextures.put(Tile.TileType.GATE, TextureLoader.loadTexture("gate_texture.png"));
        if (tileTextures.get(Tile.TileType.GATE) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a gate_texture.png textúrát.");
        }

        weaponCrateTexture = TextureLoader.loadTexture("weapon_crate_full.png");
        if (weaponCrateTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a weapon_crate_full.png textúrát.");
        }
        openCrateTexture = TextureLoader.loadTexture("weapon_crate_open.png");
        if (openCrateTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a weapon_crate_open.png textúrát.");
        }
        emptyCrateTexture = TextureLoader.loadTexture("weapon_crate_empty.png");
        if (emptyCrateTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a weapon_crate_empty.png textúrát.");
        }
        tileTextures.put(Tile.TileType.WEAPON_CRATE, weaponCrateTexture);

        Texture shopFloorTexture = TextureLoader.loadTexture("SHOP_FLOOR.png");
        if (shopFloorTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a SHOP_FLOOR.png textúrát.");
        }
        tileTextures.put(Tile.TileType.SHOP_FLOOR, shopFloorTexture);

        Texture boxTexture = TextureLoader.loadTexture("box.png");
        if (boxTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a box.png textúrát.");
        }
        tileTextures.put(Tile.TileType.BOX, boxTexture);

        boxDamageTextures = new HashMap<>();
        boxDamageTextures.put(1, TextureLoader.loadTexture("box_cracked1.png"));
        if (boxDamageTextures.get(1) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a box_cracked1.png textúrát.");
        }
        boxDamageTextures.put(2, TextureLoader.loadTexture("box_cracked2.png"));
        if (boxDamageTextures.get(2) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a box_cracked2.png textúrát.");
        }

        gateAnimationTextures = new HashMap<>();
        gateAnimationTextures.put(0, TextureLoader.loadTexture("gate_anim_01.png"));
        if (gateAnimationTextures.get(0) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a gate_anim_01.png textúrát.");
        }
        gateAnimationTextures.put(1, TextureLoader.loadTexture("gate_anim_02.png"));
        if (gateAnimationTextures.get(1) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a gate_anim_02.png textúrát.");
        }

        enemyTexture = TextureLoader.loadTexture("enemy1.png");
        if (enemyTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a enemy1.png textúrát.");
        }

        effectTextures = new HashMap<>();
        effectTextures.put(Effect.EffectType.SPEED_BOOST, TextureLoader.loadTexture("speed_boost.png"));
        if (effectTextures.get(Effect.EffectType.SPEED_BOOST) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a speed_boost.png textúrát.");
        }
        effectTextures.put(Effect.EffectType.DAMAGE_BOOST, TextureLoader.loadTexture("damage_boost.png"));
        if (effectTextures.get(Effect.EffectType.DAMAGE_BOOST) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a damage_boost.png textúrát.");
        }
        effectTextures.put(Effect.EffectType.HEALTH_REGEN, TextureLoader.loadTexture("health_regen.png"));
        if (effectTextures.get(Effect.EffectType.HEALTH_REGEN) == null) {
            System.err.println("HIBA: Nem sikerült betölteni a health_regen.png textúrát.");
        }

        teleportPadTexture = TextureLoader.loadTexture("teleport_pad.png");
        if (teleportPadTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a teleport_pad.png textúrát.");
        }
        tileTextures.put(Tile.TileType.TELEPORT_PAD, teleportPadTexture);

        fontTexture = TextureLoader.loadTexture("font.png");
        if (fontTexture == null) {
            System.err.println("HIBA: Nem sikerült betölteni a font.png textúrát.");
        }
        textRenderer = new TextRenderer("CRIT", new java.awt.Font("Arial", java.awt.Font.BOLD, 48), java.awt.Color.WHITE);

        currentDungeonSeed = System.nanoTime();
        effectRandom = new Random(currentDungeonSeed ^ 0xBEEFL);

        currentDungeon = DungeonGenerator.generateRandomDungeonWithSeed(
                currentDungeonSeed,
                32,
                tileTextures,
                enemyTexture,
                boxDamageTextures,
                gateAnimationTextures,
                weaponCrateTexture,
                openCrateTexture,
                emptyCrateTexture,
                weaponFactory,
                textRenderer,
                effectTextures,
                teleportPadTexture
        );

        applyDifficultyToEnemies();

        if (isMultiplayer && isHost && multiplayerClient != null && multiplayerClient.isConnected()) {
            multiplayerClient.sendTCPMessage("BROADCAST:DUNGEON_SEED:" + currentDungeonSeed);
        }

        float spawnX = currentDungeon.getPlayerSpawnX();
        float spawnY = currentDungeon.getPlayerSpawnY();
        player.setDungeon(currentDungeon);
        player.setSprites(walkSprite);
        player.setActiveSprites(activeWalkSprite);
        player.setWeapon(weaponFactory.createWeapon("pistol"), "pistol");
        player.setAbility(this.playerAbility);
        player.setName(this.playerName);
        player.setX(spawnX);
        player.setY(spawnY);

        for (Enemy enemy : currentDungeon.getEnemies()) {
            enemy.setTargetPlayer(this.player);
            enemy.setShowPathDebug(this.showPathDebug);
        }

        int dungeonWidthPixels = currentDungeon.getWidthTiles() * currentDungeon.getTileSize();
        int dungeonHeightPixels = currentDungeon.getHeightTiles() * currentDungeon.getTileSize();
        camera.follow(player, dungeonWidthPixels, dungeonHeightPixels);

        collisionManager = new CollisionManager(currentDungeon);
        mapRenderer = new MapRenderer();

        currentState = GameState.GAMEPLAY;
        bossDefeated = false;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);

        glfwSetKeyCallback(window, inputHandler.getKeyCallback());
        glfwSetMouseButtonCallback(window, inputHandler.getMouseButtonCallback());

        for (Enemy enemy : currentDungeon.getEnemies()) {
            if (enemy instanceof Boss) {
                boss = enemy;
                for (Enemy normalEnemy : currentDungeon.getEnemies()) {
                    if (!(normalEnemy instanceof Boss)) {
                        this.enemy = normalEnemy;
                        break;
                    }
                }
            }
        }

        upgradeScreen.reset();
        upgradeScreen.setPlayer(player);
        upgradeScreen.setDungeon(enemy, boss);
    }

    public void startGameFromSave(int saveId) {
        this.saveIdToLoad = saveId;
        this.currentState = GameState.LOAD_GAME;
    }

    private int getSelectedSaveIdFromSomewhere() {
        return this.saveIdToLoad;
    }

    private void loadSavedGame(int saveId) {
        try {
            //System.out.println("Mentett játék betöltése: " + saveId);

            Enemy savedEnemy = GameSaveHandler.loadEnemy(saveId);
            Boss savedBoss = GameSaveHandler.loadBoss(saveId);

            if (savedEnemy != null && currentDungeon != null) {
                for (Enemy enemy : currentDungeon.getEnemies()) {
                    if (!(enemy instanceof Boss)) {
                        enemy.setHealth(savedEnemy.getHealth());
                        enemy.setDamage(savedEnemy.getAttackDamage());
                        enemy.setMoveSpeed(savedEnemy.getMoveSpeed());
                        //System.out.println("Enemy adatok frissítve - HP: " + savedEnemy.getHealth());
                        break;
                    }
                }
            }

            if (savedBoss != null && currentDungeon != null) {
                for (Enemy enemy : currentDungeon.getEnemies()) {
                    if (enemy instanceof Boss) {
                        enemy.setHealth(savedBoss.getHealth());
                        enemy.setDamage(savedBoss.getAttackDamage());
                        enemy.setMoveSpeed(savedBoss.getMoveSpeed());
                        //System.out.println("Boss adatok frissítve - HP: " + savedBoss.getHealth());
                        break;
                    }
                }
            }

            //System.out.println("✅ Mentett játék sikeresen betöltve!");
        } catch (Exception e) {
            System.err.println("❌ Hiba a mentett játék betöltése közben: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initGameplayFromSave(int saveId) {
        try {
            //System.out.println("🔄 Mentett játék inicializálása: " + saveId);

            Player savedPlayer = GameSaveHandler.loadPlayer(saveId);
            if (savedPlayer == null) {
                System.err.println("Hiba: Nem sikerült betölteni a mentett playert");
                currentState = GameState.LOBBY;
                return;
            }

//            System.out.println("DEBUG - Betöltött player: " +
//                    "HP=" + savedPlayer.getHealth() +
//                    ", MaxHP=" + savedPlayer.getMaxHealth() +
//                    ", Name=" + savedPlayer.getName());

            initGameplay(savedPlayer.getName(), savedPlayer.getAbility());

//            System.out.println("DEBUG - InitGameplay után: " +
//                    "HP=" + player.getHealth() +
//                    ", MaxHP=" + player.getMaxHealth());

            player.setName(savedPlayer.getName());
            player.setAbility(savedPlayer.getAbility());
            player.setMaxHealth(savedPlayer.getMaxHealth());
            player.setHealth(savedPlayer.getHealth());
            player.setDamageBoost(savedPlayer.getDamageBoost());
            player.setCritChanceBoost(savedPlayer.getCritChanceBoost());

//            System.out.println("DEBUG - Felülírás után: " +
//                    "HP=" + player.getHealth() +
//                    ", MaxHP=" + player.getMaxHealth());

            //System.out.println("✅ Player statok felülírva!");

            loadSavedGame(saveId);
        } catch (Exception e) {
            System.err.println("❌ Hiba a mentett játék inicializálása közben: " + e.getMessage());
            e.printStackTrace();
            currentState = GameState.LOBBY;
        }
    }

    private void update(float deltaTime, double currentTime) {
        // ✨ FONTOS: Mindig feldolgozzuk a szerver üzeneteket ha multiplayer
        if (isMultiplayer && multiplayerClient != null) {
            processServerMessages();
        }

        if (currentState == GameState.ABILITY_SELECTION) {
            abilitySelectionScreen.update();
            if (abilitySelectionScreen.isSelectionComplete()) {
                if (isMultiplayer) {
                    // Multiplayer esetén csak a join üzenetet küldjük
                    startMultiplayerGame(
                            abilitySelectionScreen.getPlayerName(),
                            abilitySelectionScreen.getSelectedAbility().name(),
                            serverIp,
                            serverPort,
                            isHost
                    );
                    if (isHost) {
                        //System.out.println("🏠 HOST - AZONNALI GAMEPLAY INICIALIZÁLÁS");
                        initGameplayMultiplayer(
                                abilitySelectionScreen.getPlayerName(),
                                abilitySelectionScreen.getSelectedAbility()
                        );

                        // HOST generálja a dungeon-t
                        generateAndSendDungeon();
                    }
                } else {
                    initGameplay(
                            abilitySelectionScreen.getPlayerName(),
                            abilitySelectionScreen.getSelectedAbility()
                    );
                }
            }
        } else if (currentState == GameState.GAMEPLAY) {
            if (isMultiplayer) {
                updateGameplayMultiplayer(deltaTime, currentTime);
            } else {
                updateGameplaySingleplayer(deltaTime, currentTime);
            }
        } else if (currentState == GameState.UPGRADE_CHOICE) {
            upgradeScreen.update(inputHandler);
            handleUpgradeChoice();
        } else if (currentState == GameState.GAME_OVER) {
            handleGameOver();
        } else if (currentState == GameState.LOAD_GAME) {
            if (saveIdToLoad != -1) {
                initGameplayFromSave(saveIdToLoad);
                loadSavedGame(saveIdToLoad);
                saveIdToLoad = -1;
                currentState = GameState.GAMEPLAY;
            } else {
                currentState = GameState.LOBBY;
            }
        }
    }

    private void updateGameplayMultiplayer(float deltaTime, double currentTime) {
        // ✨ ALAPVETŐEN UGYANAZ, MINT SINGLEPLAYERBEN
        //sendPlayerPositionToServer();

        if (!player.isAlive()) {
            currentState = GameState.GAME_OVER;
            return;
        }

        processPendingGateTriggers();

        // Player update - UGYANAZ
        player.update(deltaTime, inputHandler, collisionManager, currentTime);

        // Küldjük el a pozíciót a szervernek
        sendPlayerInputToServer();

        interpolateOtherPlayers(deltaTime);
        updateOtherPlayerSpawnProtection(deltaTime);
        refreshEnemyTargets();

        if (isHost && currentDungeon != null) {
            sendEnemyUpdatesToServer();
        }

        frameCounter++;
        if (frameCounter % 10 == 0) { // Csak minden 10. frame-ben
            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy.isAlive() && !enemy.isNetworkControlled()) {
                    Player closestPlayer = findClosestPlayerToEnemy(enemy);

                    // ✨ JAVÍTÁS: Ha nincs target VAGY a régi target meghalt VAGY közelebb van másik
                    if (closestPlayer != null) {
                        Player currentTarget = enemy.getTargetPlayer();
                        boolean shouldChangeTarget =
                                currentTarget == null ||
                                        !currentTarget.isAlive() ||
                                        closestPlayer != currentTarget;

                        if (shouldChangeTarget) {
                            enemy.setTargetPlayer(closestPlayer);
                            System.out.println("🎯 Enemy " + enemy.getId() + " target: " + closestPlayer.getName() +
                                    " (previous: " + (currentTarget != null ? currentTarget.getName() : "NULL") + ")");
                        }
                    }
                }
            }

            // ✨ FONTOS: Frissítsd az ellenségeket, hogy mozogni tudjanak!
            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy.isAlive() && !enemy.isNetworkControlled()) {
                    enemy.update(deltaTime); // ← EZ HIÁNYZIK!
                }
            }

            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy.isAlive()) {
                    Player targetPlayer = enemy.getTargetPlayer();

                    // ✨ JAVÍTÁS: Ha a target meghalt, keressünk újat
                    if (targetPlayer != null && !targetPlayer.isAlive()) {
                        Player newTarget = findClosestPlayerToEnemy(enemy);
                        if (newTarget != null) {
                            enemy.setTargetPlayer(newTarget);
                            System.out.println("🔄 Enemy " + enemy.getId() + " target changed (dead): " + newTarget.getName());
                            targetPlayer = newTarget;
                        }
                    }

                    // Collision csak élő target-tel
                    if (targetPlayer != null && targetPlayer.isAlive() &&
                            !targetPlayer.hasSpawnProtection() &&
                            collisionManager.checkCollision(targetPlayer, enemy)) {

                        System.out.println("👹 ENEMY-TARGET COLLISION! EnemyID: " + enemy.getId() +
                                ", Target: " + targetPlayer.getName() +
                                ", Damage: " + enemy.getAttackDamage());
                        float damage = enemy.getAttackDamage();
                        targetPlayer.takeDamage(damage);

                        if (isMultiplayer && multiplayerClient != null && multiplayerClient.isConnected()) {
                            boolean isLocalPlayer = targetPlayer == player ||
                                    (targetPlayer.getId() >= 0 && targetPlayer.getId() == myPlayerId);
                            if (!isLocalPlayer) {
                                sendPlayerDamageUpdate(targetPlayer, damage, targetPlayer.getHealth(), targetPlayer.isAlive());
                            }
                        }

                        if (!targetPlayer.isAlive()) {
                            int defeatedPlayerId = targetPlayer.getId();
                            if (defeatedPlayerId < 0 && targetPlayer == player) {
                                defeatedPlayerId = myPlayerId;
                            }
                            forceEnemyRetarget(defeatedPlayerId);
                        }
                    }
                }
            }
        }

        // Minden frame-ben frissítsd az ellenségeket
        for (Enemy enemy : currentDungeon.getEnemies()) {
            if (enemy.isAlive() && !enemy.isNetworkControlled()) {
                enemy.update(deltaTime);
            }
        }

        enemySyncTimer += deltaTime;
        if (enemySyncTimer >= ENEMY_SYNC_INTERVAL) {
            enemySyncTimer = 0f;
        }

        // Interpoláld a többi játékost
        interpolateOtherPlayers(deltaTime);
        updateOtherPlayerSpawnProtection(deltaTime);

        // Kamera - UGYANAZ
        int dungeonWidthPixels = currentDungeon.getWidthTiles() * currentDungeon.getTileSize();
        int dungeonHeightPixels = currentDungeon.getHeightTiles() * currentDungeon.getTileSize();
        camera.follow(player, dungeonWidthPixels, dungeonHeightPixels);

        // ✨ ITT A VALÓDI KÓD - UGYANAZ, MINT SINGLEPLAYER UPDATEBEN
        // Boss ellenőrzés - UGYANAZ
        if (!bossDefeated) {
            boolean anyBossAlive = false;
            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy instanceof Boss && enemy.isAlive()) {
                    anyBossAlive = true;
                    break;
                }
            }
            if (!anyBossAlive) {
                bossDefeated = true;
                int bossGridX = currentDungeon.getBossRoomGridX();
                int bossGridY = currentDungeon.getBossRoomGridY();
                int bossWidth = currentDungeon.getBossRoomWidth();
                int bossHeight = currentDungeon.getBossRoomHeight();

                int padX = bossGridX + bossWidth / 2;
                int padY = bossGridY + bossHeight / 2;

                Tile padTile = currentDungeon.getTile(padX, padY);
                if (padTile != null) {
                    padTile.setType(Tile.TileType.TELEPORT_PAD);
                    padTile.setTexture(teleportPadTexture);
                    padTile.setIsCollidable(false);
                }
            }
        }

        // Enemy update - UGYANAZ
        for (Enemy enemy : currentDungeon.getEnemies()) {
            enemy.update(deltaTime);
        }

        // Projectile update - UGYANAZ
        Iterator<Projectile> projectileIterator = projectiles.iterator();
        while (projectileIterator.hasNext()) {
            Projectile projectile = projectileIterator.next();
            projectile.update(deltaTime);

            boolean hitSomething = false;
            int tileSize = currentDungeon.getTileSize();

            int projGridX = (int) (projectile.getX() / tileSize);
            int projGridY = (int) (projectile.getY() / tileSize);

            int minCheckX = Math.max(0, projGridX - 1);
            int maxCheckX = Math.min(currentDungeon.getWidthTiles() - 1, projGridX + 1);
            int minCheckY = Math.max(0, projGridY - 1);
            int maxCheckY = Math.min(currentDungeon.getHeightTiles() - 1, projGridY + 1);

            boolean canAffectWorld = hasSharedWorldAuthority();

            for (int x = minCheckX; x <= maxCheckX; x++) {
                for (int y = minCheckY; y <= maxCheckY; y++) {
                    Tile tile = currentDungeon.getTiles()[x][y];
                    if (tile != null && tile.isSolid()) {
                        if (collisionManager.checkTileCollision(projectile, tile, x, y)) {
                            projectile.setAlive(false);
                            if (tile.getType() == Tile.TileType.BOX && canAffectWorld) {
                                tile.takeDamage(1);
                                boolean destroyed = tile.isDestroyed();

                                Effect spawnedEffect = null;
                                if (destroyed) {
                                    tile.setType(Tile.TileType.FLOOR);
                                    tile.setTexture(tileTextures.get(Tile.TileType.FLOOR));
                                    tile.setIsCollidable(false);
                                    spawnedEffect = spawnRandomEffect(tile.getX(), tile.getY());
                                } else {
                                    tile.updateTextureByHealth();
                                }

                                if (isMultiplayer && multiplayerClient != null && multiplayerClient.isConnected()) {
                                    sendTileStateUpdate(x, y, tile.getHealth(), destroyed);
                                }

                                if (spawnedEffect != null && isMultiplayer && isHost && multiplayerClient != null && multiplayerClient.isConnected()) {
                                    broadcastEffectSpawn(spawnedEffect);
                                }
                            }
                            hitSomething = true;
                            break;
                        }
                    }
                }
                if (hitSomething) break;
            }

            if (hitSomething) {
                if (!projectile.isAlive()) {
                    projectileIterator.remove();
                }
                continue;
            }

            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy.isAlive() && collisionManager.checkCollision(projectile, enemy)) {
                    float finalDamage = player.calculateFinalDamage(projectile.getDamage(), enemy);
                    enemy.takeDamage(finalDamage);
                    notifyEnemyDamage(enemy, finalDamage);
                    projectile.setAlive(false);
                    hitSomething = true;
                    break;
                }
            }

            if (!projectile.isAlive()) {
                projectileIterator.remove();
            }
        }

        // Teleport pad ellenőrzés - UGYANAZ
        if (bossDefeated && currentState == GameState.GAMEPLAY) {
            int playerGridX = (int) ((player.getX() + player.getWidth() / 2) / currentDungeon.getTileSize());
            int playerGridY = (int) ((player.getY() + player.getHeight() / 2) / currentDungeon.getTileSize());
            if (playerGridX >= 0 && playerGridX < currentDungeon.getWidthTiles() &&
                    playerGridY >= 0 && playerGridY < currentDungeon.getHeightTiles()) {
                Tile playerTile = currentDungeon.getTiles()[playerGridX][playerGridY];
                if (playerTile != null && playerTile.getType() == Tile.TileType.TELEPORT_PAD) {
                    currentState = GameState.UPGRADE_CHOICE;
                }
            }
        }

        // Effect kezelés - UGYANAZ
        checkEffectCollision();
        updatePlayerEffects(deltaTime);
        removeCollectedEffects();
        currentDungeon.getEnemies().removeIf(enemy -> !enemy.isAlive());
    }

    private Player findClosestPlayerToEnemy(Enemy enemy) {
        Player closestPlayer = findClosestTargetForEnemy(enemy, false);
        if (closestPlayer == null) {
            closestPlayer = findClosestTargetForEnemy(enemy, true);
        }
        return closestPlayer;
    }

    private Player findClosestTargetForEnemy(Enemy enemy, boolean ignoreSpawnProtection) {
        Player closestPlayer = null;
        float closestDistance = Float.MAX_VALUE;

        // ✨ CSAK élő játékosokat vegyünk figyelembe!

        // Saját játékos - CSAK HA ÉL
        if (player != null && player.isAlive() && !player.hasSpawnProtection()) {
            float distance = calculateDistance(enemy.getX(), enemy.getY(),
                    player.getX(), player.getY());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPlayer = player;
            }
        }

        if (isMultiplayer) {
            for (Player otherPlayer : otherPlayers.values()) {
                if (otherPlayer != null && otherPlayer.isAlive() && !otherPlayer.hasSpawnProtection()) { // ✨ FONTOS: null check + alive check
                    float distance = calculateDistance(enemy.getX(), enemy.getY(),
                            otherPlayer.getX(), otherPlayer.getY());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestPlayer = otherPlayer;
                    }
                }
            }
        }

        if (closestPlayer != null) {
            System.out.println("🔍 Enemy " + enemy.getId() + " closest target: " +
                    closestPlayer.getName() + " (distance: " + closestDistance +
                    ", ignoreSpawnProtection=" + ignoreSpawnProtection + ")");
        }

        return closestPlayer;
    }

    private float calculateDistance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void forceEnemyRetarget(int deadPlayerId) {
        if (currentDungeon == null) {
            return;
        }

        Player deadPlayer = null;
        if (deadPlayerId == myPlayerId || deadPlayerId < 0) {
            deadPlayer = player;
        } else if (otherPlayers.containsKey(deadPlayerId)) {
            deadPlayer = otherPlayers.get(deadPlayerId);
        }

        for (Enemy enemy : currentDungeon.getEnemies()) {
            if (!enemy.isAlive()) {
                continue;
            }

            Player currentTarget = enemy.getTargetPlayer();
            boolean targetInvalid = currentTarget == null || !currentTarget.isAlive();
            if (!targetInvalid && currentTarget.hasSpawnProtection()) {
                targetInvalid = true;
            }
            if (!targetInvalid && deadPlayer != null) {
                targetInvalid = currentTarget == deadPlayer;
            }
            if (!targetInvalid && deadPlayerId >= 0 && currentTarget != null && currentTarget.getId() == deadPlayerId) {
                targetInvalid = true;
            }

            if (targetInvalid) {
                Player newTarget = findClosestPlayerToEnemy(enemy);
                if (newTarget != null && newTarget.isAlive()) {
                    enemy.setTargetPlayer(newTarget);
                } else {
                    enemy.setTargetPlayer(null);
                }
            }
        }
    }

    private void refreshEnemyTargets() {
        if (currentDungeon == null) {
            return;
        }

        for (Enemy enemy : currentDungeon.getEnemies()) {
            if (!enemy.isAlive()) {
                continue;
            }

            Player currentTarget = enemy.getTargetPlayer();
            if (currentTarget == null || !currentTarget.isAlive() || currentTarget.hasSpawnProtection()) {
                Player newTarget = findClosestPlayerToEnemy(enemy);
                if (newTarget != null && newTarget.isAlive()) {
                    enemy.setTargetPlayer(newTarget);
                } else {
                    enemy.setTargetPlayer(null);
                }
            }
        }
    }

    private void updateGameplaySingleplayer(float deltaTime, double currentTime) {
        if (!player.isAlive()) {
            currentState = GameState.GAME_OVER;
            return;
        }

        player.update(deltaTime, inputHandler, collisionManager, currentTime);
        refreshEnemyTargets();

        int dungeonWidthPixels = currentDungeon.getWidthTiles() * currentDungeon.getTileSize();
        int dungeonHeightPixels = currentDungeon.getHeightTiles() * currentDungeon.getTileSize();
        camera.follow(player, dungeonWidthPixels, dungeonHeightPixels);

        if (!bossDefeated) {
            boolean anyBossAlive = false;
            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy instanceof Boss && enemy.isAlive()) {
                    anyBossAlive = true;
                    break;
                }
            }
            if (!anyBossAlive) {
                bossDefeated = true;
                int bossGridX = currentDungeon.getBossRoomGridX();
                int bossGridY = currentDungeon.getBossRoomGridY();
                int bossWidth = currentDungeon.getBossRoomWidth();
                int bossHeight = currentDungeon.getBossRoomHeight();

                int padX = bossGridX + bossWidth / 2;
                int padY = bossGridY + bossHeight / 2;

                Tile padTile = currentDungeon.getTile(padX, padY);
                if (padTile != null) {
                    padTile.setType(Tile.TileType.TELEPORT_PAD);
                    padTile.setTexture(teleportPadTexture);
                    padTile.setIsCollidable(false);
                }
            }
        }

        for (Enemy enemy : currentDungeon.getEnemies()) {
            enemy.update(deltaTime);
        }

        Iterator<Projectile> projectileIterator = projectiles.iterator();
        while (projectileIterator.hasNext()) {
            Projectile projectile = projectileIterator.next();
            projectile.update(deltaTime);

            boolean hitSomething = false;
            int tileSize = currentDungeon.getTileSize();

            int projGridX = (int) (projectile.getX() / tileSize);
            int projGridY = (int) (projectile.getY() / tileSize);

            int minCheckX = Math.max(0, projGridX - 1);
            int maxCheckX = Math.min(currentDungeon.getWidthTiles() - 1, projGridX + 1);
            int minCheckY = Math.max(0, projGridY - 1);
            int maxCheckY = Math.min(currentDungeon.getHeightTiles() - 1, projGridY + 1);

            boolean canAffectWorld = hasSharedWorldAuthority();

            for (int x = minCheckX; x <= maxCheckX; x++) {
                for (int y = minCheckY; y <= maxCheckY; y++) {
                    Tile tile = currentDungeon.getTiles()[x][y];
                    if (tile != null && tile.isSolid()) {
                        if (collisionManager.checkTileCollision(projectile, tile, x, y)) {
                            projectile.setAlive(false);
                            if (tile.getType() == Tile.TileType.BOX && canAffectWorld) {
                                tile.takeDamage(1);
                                boolean destroyed = tile.isDestroyed();
                                if (destroyed) {
                                    tile.setType(Tile.TileType.FLOOR);
                                    tile.setTexture(tileTextures.get(Tile.TileType.FLOOR));
                                    Effect spawnedEffect = spawnRandomEffect(tile.getX(), tile.getY());
                                    if (spawnedEffect != null && isMultiplayer && isHost && multiplayerClient != null && multiplayerClient.isConnected()) {
                                        broadcastEffectSpawn(spawnedEffect);
                                    }
                                } else {
                                    tile.updateTextureByHealth();
                                }
                                sendTileStateUpdate(x, y, tile.getHealth(), destroyed);
                            }
                            hitSomething = true;
                            break;
                        }
                    }
                }
                if (hitSomething) break;
            }

            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy.isAlive() && collisionManager.checkCollision(projectile, enemy)) {
                    float finalDamage = player.calculateFinalDamage(projectile.getDamage(), enemy);
                    enemy.takeDamage(finalDamage);
                    projectile.setAlive(false);
                    hitSomething = true;
                    break;
                }
            }

            if (!projectile.isAlive()) {
                projectileIterator.remove();
            }
        }

        if (bossDefeated && currentState == GameState.GAMEPLAY) {
            int playerGridX = (int) ((player.getX() + player.getWidth() / 2) / currentDungeon.getTileSize());
            int playerGridY = (int) ((player.getY() + player.getHeight() / 2) / currentDungeon.getTileSize());
            if (playerGridX >= 0 && playerGridX < currentDungeon.getWidthTiles() &&
                    playerGridY >= 0 && playerGridY < currentDungeon.getHeightTiles()) {
                Tile playerTile = currentDungeon.getTiles()[playerGridX][playerGridY];
                if (playerTile != null && playerTile.getType() == Tile.TileType.TELEPORT_PAD) {
                    currentState = GameState.UPGRADE_CHOICE;
                }
            }
        }

        checkEffectCollision();
        updatePlayerEffects(deltaTime);
        removeCollectedEffects();
        currentDungeon.getEnemies().removeIf(enemy -> !enemy.isAlive());
    }

    private void notifyEnemyDamage(Enemy enemy, float damageAmount) {
        if (!isMultiplayer || multiplayerClient == null || !multiplayerClient.isConnected()) {
            return;
        }

        if (enemy == null) {
            return;
        }

        int enemyId = enemy.getId();
        if (enemyId < 0) {
            return;
        }

        float newHealth = Math.max(0f, enemy.getHealth());
        boolean isAlive = enemy.isAlive();

        multiplayerClient.sendEnemyDamage(enemyId, damageAmount, newHealth, isAlive);
    }

    private void sendPlayerInputToServer() {
        //System.out.println("🔍 sendPlayerInputToServer() ELÉRVE");

        // ✨ MINDIG küldjünk valamit, függetlenül a feltételektől
        if (multiplayerClient != null && player != null) {
            float x = player.getX();
            float y = player.getY();

            //System.out.println("📤 KÜLDÖM: " + x + ", " + y);
            multiplayerClient.sendPlayerPosition(x, y);

            // Teszt üzenet
            multiplayerClient.sendPlayerInput("TEST");
            //System.out.println("✅ KÜLDVE");
        } else {
            System.out.println("❌ NINCS MEG A KLIENS VAGY PLAYER");
            System.out.println("   Client: " + (multiplayerClient != null));
            System.out.println("   Player: " + (player != null));
        }
    }

    private void handleServerMessage(String message) {
        // ✨ ELŐSZÖR ellenőrizzük, hogy UDP vagy TCP üzenet-e
        if (isUDPMessage(message)) {
            handleUDPMessage(message);
        } else {
            handleTCPMessage(message);
        }
    }

    private boolean isUDPMessage(String message) {
        // UDP üzenet: "4:PLAYER_POSITION:2295.0,3223.0"
        // Formátum: playerId:command:data
        String[] parts = message.split(":", 3);
        if (parts.length >= 3) {
            try {
                Integer.parseInt(parts[0]); // Az első rész playerId számként
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private void processServerMessages() {
        if (!isMultiplayer || multiplayerClient == null) return;

        List<String> messages = multiplayerClient.getReceivedMessages();
        for (String message : messages) {
            //handleServerMessage(message);
        	try {
                handleServerMessage(message);
            } catch (Exception e) {
                System.err.println("❌ Hiba a szerver üzenet feldolgozásakor: " + message);
                e.printStackTrace();
            }
        }
    }

    private int getConnectedMultiplayerPlayerCount() {
        int detectedCount = getDetectedMultiplayerPlayerCount();
        if (allPlayersReady) {
            return Math.max(detectedCount, MULTIPLAYER_MAX_PLAYERS);
        }
        return detectedCount;
    }
    
    private int getDetectedMultiplayerPlayerCount() {
        Set<Integer> ids = new HashSet<>(serverPlayerStates.keySet());
        if (myPlayerId > 0) {
            ids.add(myPlayerId);
        }
        return Math.max(1, ids.size());
    }

    private void updateWaitingPlayerCountText() {
        String newText = getConnectedMultiplayerPlayerCount() + "/" + MULTIPLAYER_MAX_PLAYERS;
        if (newText.equals(waitingPlayerCountText)) {
            return;
        }

        waitingPlayerCountText = newText;

        if (waitingPlayerCountRenderer != null) {
            waitingPlayerCountRenderer.cleanup();
        }

        waitingPlayerCountRenderer = new TextRenderer(
                waitingPlayerCountText,
                new java.awt.Font("Arial", java.awt.Font.PLAIN, 28),
                java.awt.Color.LIGHT_GRAY
        );
    }

    private void handleUDPMessage(String message) {
        // ✨ RÉSZLETES KIÍRÁS A KAPOTT UDP ÜZENETRŐL
//        System.out.println("=======================================");
//        System.out.println("🎯 UDP ÜZENET FELDOLGOZÁSA - GAMEMANAGER");
//        System.out.println("=======================================");
//        System.out.println("📨 NYERS ÜZENET: " + message);
//        System.out.println("📏 Üzenet hossza: " + message.length() + " karakter");

        String[] parts = message.split(":", 3);
        if (parts.length < 3) {
            System.err.println("❌ HIBA: Hiányos UDP üzenet formátum!");
            System.err.println("   Elvárt: playerId:command:data");
            System.err.println("   Kapott: " + message);
            return;
        }

//        System.out.println("🔍 FORMÁTUM ELEMZÉS:");
//        System.out.println("   - Player ID: " + parts[0]);
//        System.out.println("   - Command: " + parts[1]);
//        System.out.println("   - Data: " + parts[2]);

        try {
            int playerId = Integer.parseInt(parts[0]);
            String command = parts[1];
            String data = parts[2];

            switch (command) {
                case "PLAYER_POSITION":
                    //System.out.println("📍 POZÍCIÓ FRISSÍTÉS: " + data);
                    handlePlayerPositionUpdate(playerId + ":" + data);
                    break;
                case "ENEMY_UPDATE":
                    //System.out.println("👹 ELLENSÉG FRISSÍTÉS: " + data);
                    handleEnemyUpdate(data);
                    break;
                case "PROJECTILE_CREATED":
                    //System.out.println("💥 LÖVEDÉK LÉTREHOZÁS: " + data);
                    handleProjectileCreated(data);
                    break;
                case "PROJECTILE_UPDATE":
                    //System.out.println("💥 LÖVEDÉK FRISSÍTÉS: " + data);
                    handleProjectileUpdate(data);
                    break;
                case "PLAYER_ACTION":
                    //System.out.println("🎮 JÁTÉKOS AKCIÓ: " + data);
                    handlePlayerAction(playerId + ":" + data);
                    break;
                case "PLAYER_DAMAGE":
                    handlePlayerDamageUpdate(data);
                    break;
                case "ENEMY_DAMAGE":
                    handleEnemyDamageUpdate(data);
                    break;
                case "PLAYER_ELIMINATED":
                    handlePlayerEliminated(data);
                    break;
                default:
                    //System.out.println("❓ ISMERETLEN UDP COMMAND: " + command);
                    System.out.println("   Teljes üzenet: " + message);
                    break;
            }

            //System.out.println("✅ UDP ÜZENET SIKERESEN FELDOLGOZVA");

        } catch (NumberFormatException e) {
            System.err.println("❌ HIBA: Érvénytelen Player ID formátum: " + parts[0]);
            System.err.println("   Teljes üzenet: " + message);
        } catch (Exception e) {
            System.err.println("❌ HIBA az UDP üzenet feldolgozásában: " + e.getMessage());
            e.printStackTrace();
        }

        //System.out.println("=======================================");
    }

    private void handleTCPMessage(String message) {
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String data = parts.length > 1 ? parts[1] : "";

        //System.out.println("📨 TCP SZERVER ÜZENET: " + command + " | " + data);

        switch (command) {
            case "PLAYER_ID":
                myPlayerId = Integer.parseInt(data);
                multiplayerClient.setPlayerId(myPlayerId);
                updateWaitingPlayerCountText();
                if (player != null) {
                    player.setId(myPlayerId);
                }
                if (isHost) {
                    hostPlayerId = myPlayerId;
                    hostPresent = true;
                    hostAlive = true;
                }
                //System.out.println("🎮 PLAYER ID FRISSÍTVE: " + myPlayerId);
                break;

            case "ALL_PLAYERS_READY":
                //System.out.println("✅ MINDENKI KÉSZ – VÁRJUK A DUNGEON SEED-ET");
            	//if (isHost && currentState != GameState.GAMEPLAY) {
            	//    generateAndSendDungeon();
            	//}
            	//System.out.println("✅ MINDENKI KÉSZ – VÁRJUK A SZERVER DUNGEON SEED-JÉT");
                // Fontos: ne generáljon seed-et kliens oldalon (host sem),
                // mert az eltérő állapotot okozhat. A szerver küldi mindenkinek a DUNGEON_SEED-et.
            	allPlayersReady = true;
                updateWaitingPlayerCountText();
                break;
            
            case "GAME_STARTING":
                // A tényleges indulás a DUNGEON_SEED üzenetnél történik.
                // Itt csak biztosítjuk, hogy a kliens ne maradjon hibás állapotban.
            	allPlayersReady = true;
                updateWaitingPlayerCountText();
                if (currentState == GameState.LOBBY && player == null && playerAbility != null) {
                    initGameplayMultiplayer(playerName, playerAbility);
                }
                break;

            case "DUNGEON_SEED":
                if (currentState == GameState.GAMEPLAY) {
                    //System.out.println("⚠️  MÁR GAMEPLAY STATE-BEN VAGYOK - NEM FOGADOK ÚJ DUNGEON SEED-ET");
                    return;
                }

                long seed = Long.parseLong(data);
                //System.out.println("🌱 DUNGEON SEED ÉRKEZETT A SZERVERTŐL: " + seed);

                // ✨ Ha még nincs inicializálva, inicializáljuk
                if (player == null) {
                    initGameplayMultiplayer(playerName, playerAbility);
                }

                // ✨ Generáljuk a dungeon-t a kapott seed-del
                generateDungeonWithSeed(seed);

                // ✨ MOST beállítjuk az enemy target-eket, mert már van dungeon
                for (Enemy enemy : currentDungeon.getEnemies()) {
                    enemy.setTargetPlayer(this.player);
                    enemy.setShowPathDebug(this.showPathDebug);
                }

                // ✨ MOST beállítjuk a boss referenciákat
                for (Enemy enemy : currentDungeon.getEnemies()) {
                    if (enemy instanceof Boss) {
                        boss = enemy.clone();
                        for (Enemy normalEnemy : currentDungeon.getEnemies()) {
                            if (!(normalEnemy instanceof Boss)) {
                                this.enemy = normalEnemy.clone();
                                break;
                            }
                        }
                    }
                }

                upgradeScreen.setDungeon(enemy, boss);
                
                syncPendingOtherPlayers();

                // ✨ MOST mehetünk gameplay state-be
                currentState = GameState.GAMEPLAY;
                //System.out.println("✅ CLIENT GAMEPLAY STATE BEÁLLÍTVA");
                break;


            case "BROADCAST":
                if (data.startsWith("DUNGEON_SEED:")) {
                    long broadcastSeed = Long.parseLong(data.substring(13));
                    //System.out.println("🌱 BROADCAST DUNGEON SEED: " + broadcastSeed);
                    if (!isHost) {
                        // ✨ JAVÍTÁS: Ugyanaz a logika mint a DUNGEON_SEED esetén
                        generateDungeonWithSeed(broadcastSeed);

                        if (player == null) {
                            initPlayerForMultiplayer();
                        } else {
                            player.setX(currentDungeon.getPlayerSpawnX());
                            player.setY(currentDungeon.getPlayerSpawnY());
                            player.setDungeon(currentDungeon);
                            player.activateSpawnProtection();
                            announceSpawnStateToServer();
                        }
                        syncPendingOtherPlayers();
                        currentState = GameState.GAMEPLAY;
                    }
                }
                break;

            case "PLAYER_JOINED":
                handlePlayerJoined(data);
                break;

            case "PLAYER_DISCONNECTED":
                handlePlayerDisconnected(data);
                break;

            case "PLAYER_POSITION":
                handlePlayerPositionUpdate(data);
                break;

            case "PLAYER_ACTION":
                handlePlayerAction(data);
                break;

            case "PLAYER_DAMAGE":
                handlePlayerDamageUpdate(data);
                break;

            case "ENEMY_DAMAGE":
                handleEnemyDamageUpdate(data);
                break;

            case "PLAYER_ELIMINATED":
                handlePlayerEliminated(data);
                break;

            case "EFFECT_SPAWN":
                handleEffectSpawnMessage(data);
                break;

            case "EFFECT_PICKUP":
                handleEffectPickupMessage(data);
                break;

            case "GATE_TRIGGER":
                handleGateTriggerMessage(data);
                break;

            case "PROJECTILE_CREATED":
                handleProjectileCreated(data);
                break;

            case "PROJECTILE_REMOVED":
                handleProjectileRemoved(data);
                break;

            case "PROJECTILE_HIT":
                handleProjectileHit(data);
                break;

            case "ENEMY_UPDATE":
                handleEnemyUpdate(data);
                break;

            case "ENEMY_CREATED":
                //System.out.println("👹 KEZDETI ELLENSÉG: " + data);
                handleEnemyCreated(data);
                break;

            case "PROJECTILE_UPDATE":
                handleProjectileUpdate(data);
                break;

            case "TILE_UPDATE":
                handleTileUpdate(data);
                break;

            case "GAME_STATE":
                updateFromGameState(data);
                break;

            default:
                System.out.println("❓ ISMERETLEN ÜZENET: " + message);
                break;
        }
    }

    private void handleEnemyDamageUpdate(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        try {
            String[] parts = data.split(":");
            if (parts.length < 4) {
                System.err.println("❌ Invalid ENEMY_DAMAGE data: " + data);
                return;
            }

            int enemyId = Integer.parseInt(parts[0]);
            float damageAmount = Float.parseFloat(parts[1]);
            float newHealth = Float.parseFloat(parts[2]);
            boolean isAlive = Boolean.parseBoolean(parts[3]);

            Enemy targetEnemy = findEnemyById(enemyId);
            if (targetEnemy == null) {
                System.out.println("⚠️ ENEMY_DAMAGE frissítés ismeretlen ellenségre: " + enemyId);
                return;
            }

            float clampedHealth = Math.max(0f, newHealth);

            targetEnemy.setHealth(clampedHealth);
            targetEnemy.setAlive(isAlive);

            if (!isAlive) {
                targetEnemy.setNetworkControlled(false);
            }

            // Ha az enemy jelenleg nem látható kritikus visszajelzést, de sebzést kapott, villantsuk meg
            if (damageAmount > 0f) {
                targetEnemy.activateCritIndicator();
            }

        } catch (Exception e) {
            System.err.println("❌ Error in handleEnemyDamageUpdate: " + e.getMessage());
        }
    }

    private void sendPlayerPositionToServer() {
        if (!isMultiplayer || multiplayerClient == null || player == null) {
            return;
        }

        // ✨ MINDEN FRAME-BEN küldjük a pozíciót
        float x = player.getX();
        float y = player.getY();

        // UDP-n keresztül küldjük a gyors pozíció frissítéseket
        multiplayerClient.sendPlayerPosition(x, y);

        // ✨ KÜLDJÜK EL A PLAYER_STATE-ET IS TCP-N KERESZTÜL (csak pozíció)
        String stateMessage = "PLAYER_STATE:" +
                myPlayerId + ":" +
                x + ":" + y + ":" +  // ✨ Csak pozíció, nincs isAlive
                player.getName() + ":" +
                player.getAbility().name();

        multiplayerClient.sendTCPMessage(stateMessage);

        // ✨ DEBUG: Csak néha logoljunk
        if (System.currentTimeMillis() % 2000 < 16) { // 2 másodpercenként
            System.out.println("📤 POSITION SENT - X: " + x + ", Y: " + y);
        }
    }

    private void initPlayerForMultiplayer() {
        if (currentDungeon == null) {
            System.err.println("❌ Cannot init player: currentDungeon is null!");
            return;
        }

//        System.out.println("🎮 Initializing player for multiplayer at: " +
//                currentDungeon.getPlayerSpawnX() + ", " + currentDungeon.getPlayerSpawnY());

        // ✨ FONTOS: Textúrák betöltésének ellenőrzése
        if (playerIdleTexture == null) {
            playerIdleTexture = TextureLoader.loadTexture("character1.png");
            System.out.println("🔍 Player texture loaded: " + (playerIdleTexture != null));
        }

        // Player létrehozása
        player = new Player(
                currentDungeon.getPlayerSpawnX(),
                currentDungeon.getPlayerSpawnY(),
                50, 50, playerIdleTexture, window, weaponFactory,
                tileTextures.get(Tile.TileType.FLOOR), textRenderer
        );

        player.setId(myPlayerId);
        player.activateSpawnProtection();
        announceSpawnStateToServer();

        setupPlayerDamageListener(player);
        setupPlayerGateListener(player);

        // ✨ FONTOS: Sprite-ok beállítása
        Sprite walkSprite = null;
        if (walkFrames != null && !walkFrames.isEmpty()) {
            walkSprite = new Sprite(walkFrames, 0.1f, true);
            System.out.println("🔍 Walk sprite created: " + (walkSprite != null));
        }

        Sprite activeWalkSprite = null;
        if (activeWalkFrames != null && !activeWalkFrames.isEmpty()) {
            activeWalkSprite = new Sprite(activeWalkFrames, 0.1f, false);
            System.out.println("🔍 Active walk sprite created: " + (activeWalkSprite != null));
        }

        player.setDungeon(currentDungeon);
        player.setSprites(walkSprite);
        player.setActiveSprites(activeWalkSprite);
        player.setWeapon(weaponFactory.createWeapon("pistol"), "pistol");
        player.setAbility(playerAbility);
        player.setName(playerName);

        // ✨ FONTOS: Collision manager létrehozása
        collisionManager = new CollisionManager(currentDungeon);
        //System.out.println("🔍 Collision manager created: " + (collisionManager != null));

        // ✨ FONTOS: MapRenderer létrehozása
        mapRenderer = new MapRenderer();
        //System.out.println("🔍 MapRenderer created: " + (mapRenderer != null));

        // Kamera beállítása
        int dungeonWidthPixels = currentDungeon.getWidthTiles() * currentDungeon.getTileSize();
        int dungeonHeightPixels = currentDungeon.getHeightTiles() * currentDungeon.getTileSize();
        camera.follow(player, dungeonWidthPixels, dungeonHeightPixels);

        //System.out.println("🔍 Camera set to follow player");

        //System.out.println("✅ Player initialized for multiplayer - COMPLETE");
    }

    // GameManager.java - setupPlayerDamageListener
    private void setupPlayerDamageListener(Player player) {
        if (player == null) {
            System.err.println("❌ Cannot setup damage listener - player is null");
            return;
        }

        player.setDamageListener((damagedPlayer, damageAmount, newHealth, isAlive) -> {
            System.out.println("💥 LOCAL DAMAGE LISTENER: " + damageAmount +
                    " damage, Health: " + newHealth + ", Alive: " + isAlive);

            // ✨ FONTOS: AZONNALI LOKÁLIS FRISSÍTÉS - ez hiányzik!
            damagedPlayer.setHealth(newHealth);
            damagedPlayer.setAlive(isAlive);

            // ✨ Health bar frissítése
            System.out.println("❤️  SAJÁT ÉLETERŐ FRISSÍTVE LOKÁLISAN: " + newHealth + " HP");

            if (!isAlive) {
                System.out.println("💀 PLAYER DIED - Switching to GAME_OVER");
                currentState = GameState.GAME_OVER;
                if (isMultiplayer) {
                    int deadId = damagedPlayer.getId() >= 0 ? damagedPlayer.getId() : myPlayerId;
                    forceEnemyRetarget(deadId);
                }
            }

            // Küldés a szervernek
            if (isMultiplayer && multiplayerClient != null && multiplayerClient.isConnected()) {
                System.out.println("📤 SENDING DAMAGE TO SERVER: " + damageAmount + " damage");
                sendPlayerDamageUpdate(damagedPlayer, damageAmount, newHealth, isAlive);
            }
        });

        System.out.println("✅ Damage listener setup complete");
    }

    private void setupPlayerGateListener(Player player) {
        if (player == null) {
            return;
        }

        player.setGateOpenListener(this::handleGateOpenRequest);
    }

    private void handleGateOpenRequest(List<Tile> gateGroup) {
        if (gateGroup == null || gateGroup.isEmpty()) {
            return;
        }

        String eventKey = buildGateEventKey(gateGroup);
        if (processedGateEvents.contains(eventKey)) {
            return;
        }

        processedGateEvents.add(eventKey);
        if (player != null) {
            player.triggerGateAnimation(gateGroup);
        }

        if (isMultiplayer && multiplayerClient != null && multiplayerClient.isConnected()) {
            multiplayerClient.sendTCPMessage("GATE_TRIGGER:" + eventKey);
        }
    }

    private String buildGateEventKey(List<Tile> gateGroup) {
        List<Tile> sorted = new ArrayList<>(gateGroup);
        sorted.sort(Comparator.comparingInt(Tile::getGridX).thenComparingInt(Tile::getGridY));

        StringBuilder builder = new StringBuilder();
        for (Tile tile : sorted) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(tile.getGridX()).append(',').append(tile.getGridY());
        }
        return builder.toString();
    }

    private List<int[]> parseGateCoordinates(String gateData) {
        List<int[]> coordinates = new ArrayList<>();
        if (gateData == null || gateData.isEmpty()) {
            return coordinates;
        }

        String[] entries = gateData.split("\\|");
        for (String entry : entries) {
            String[] parts = entry.split(",");
            if (parts.length != 2) {
                continue;
            }

            try {
                int gridX = Integer.parseInt(parts[0]);
                int gridY = Integer.parseInt(parts[1]);
                coordinates.add(new int[]{gridX, gridY});
            } catch (NumberFormatException ignored) {
            }
        }

        return coordinates;
    }

    private void applyGateTileUpdates(String gateData) {
        if (currentDungeon == null) {
            return;
        }

        List<int[]> coordinates = parseGateCoordinates(gateData);
        if (coordinates.isEmpty()) {
            return;
        }

        Map<Tile.TileType, Texture> textureMap = tileTextures;
        Set<Long> coordinateKeys = new HashSet<>();

        for (int[] coord : coordinates) {
            if (coord == null || coord.length < 2) {
                continue;
            }

            int gridX = coord[0];
            int gridY = coord[1];

            if (gridX < 0 || gridY < 0 ||
                    gridX >= currentDungeon.getWidthTiles() ||
                    gridY >= currentDungeon.getHeightTiles()) {
                continue;
            }

            long key = (((long) gridX) << 32) | (gridY & 0xffffffffL);
            coordinateKeys.add(key);

            Tile tile = currentDungeon.getTiles()[gridX][gridY];
            if (tile == null) {
                continue;
            }

            tile.setType(Tile.TileType.FLOOR);
            if (textureMap != null && textureMap.containsKey(Tile.TileType.FLOOR)) {
                tile.setTexture(textureMap.get(Tile.TileType.FLOOR));
            }
            tile.setIsCollidable(false);
        }

        if (coordinateKeys.isEmpty() || currentDungeon.gateCorridorGroups == null) {
            return;
        }

        Iterator<List<Tile>> iterator = currentDungeon.gateCorridorGroups.iterator();
        while (iterator.hasNext()) {
            List<Tile> group = iterator.next();
            if (group == null || group.isEmpty()) {
                continue;
            }

            boolean matches = true;
            for (Tile tile : group) {
                if (tile == null) {
                    continue;
                }

                long key = (((long) tile.getGridX()) << 32) | (tile.getGridY() & 0xffffffffL);
                if (!coordinateKeys.contains(key)) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                iterator.remove();
            }
        }
    }

    private void handleGateTriggerMessage(String gateData) {
        if (gateData == null || gateData.isEmpty()) {
            return;
        }

        if (player == null || currentDungeon == null) {
            pendingGateTriggers.add(gateData);
            return;
        }

        if (processedGateEvents.contains(gateData)) {
            applyGateTileUpdates(gateData);
            return;
        }

        List<Tile> gateGroup = findGateGroupByEventKey(gateData);
        if (gateGroup != null) {
            boolean hasGateTile = false;
            for (Tile tile : gateGroup) {
                if (tile != null && tile.getType() == Tile.TileType.GATE) {
                    hasGateTile = true;
                    break;
                }
            }

            processedGateEvents.add(gateData);
            if (hasGateTile) {
                player.triggerGateAnimation(gateGroup);
            } else {
                applyGateTileUpdates(gateData);
            }
        } else {
            applyGateTileUpdates(gateData);
            processedGateEvents.add(gateData);
        }
    }

    private void processPendingGateTriggers() {
        if (pendingGateTriggers.isEmpty() || player == null || currentDungeon == null) {
            return;
        }

        Iterator<String> iterator = pendingGateTriggers.iterator();
        while (iterator.hasNext()) {
            String gateData = iterator.next();
            if (processedGateEvents.contains(gateData)) {
                iterator.remove();
                continue;
            }

            List<Tile> gateGroup = findGateGroupByEventKey(gateData);
            if (gateGroup != null) {
                boolean hasGateTile = false;
                for (Tile tile : gateGroup) {
                    if (tile != null && tile.getType() == Tile.TileType.GATE) {
                        hasGateTile = true;
                        break;
                    }
                }

                processedGateEvents.add(gateData);
                if (hasGateTile) {
                    player.triggerGateAnimation(gateGroup);
                } else {
                    applyGateTileUpdates(gateData);
                }
                iterator.remove();
            } else {
                applyGateTileUpdates(gateData);
                processedGateEvents.add(gateData);
                iterator.remove();
            }
        }
    }

    private List<Tile> findGateGroupByEventKey(String eventKey) {
        if (currentDungeon == null || currentDungeon.gateCorridorGroups == null) {
            return null;
        }

        String[] entries = eventKey.split("\\|");
        if (entries.length == 0) {
            return null;
        }

        String[] firstCoords = entries[0].split(",");
        if (firstCoords.length != 2) {
            return null;
        }

        try {
            int targetX = Integer.parseInt(firstCoords[0]);
            int targetY = Integer.parseInt(firstCoords[1]);

            for (List<Tile> group : currentDungeon.gateCorridorGroups) {
                if (group == null) {
                    continue;
                }

                for (Tile tile : group) {
                    if (tile.getGridX() == targetX && tile.getGridY() == targetY) {
                        return group;
                    }
                }
            }
        } catch (NumberFormatException ignored) {
        }

        return null;
    }

    private void sendTileStateUpdate(int gridX, int gridY, float health, boolean destroyed) {
        if (!isMultiplayer || multiplayerClient == null || !multiplayerClient.isConnected()) {
            return;
        }

        String message = String.format(Locale.US, "%d,%d,%.1f,%b", gridX, gridY, health, destroyed);
        multiplayerClient.sendTCPMessage("TILE_UPDATE:" + message);
    }

    private boolean hasSharedWorldAuthority() {
        if (!isMultiplayer) {
            return true;
        }

        if (isHost) {
            return true;
        }

        return multiplayerClient == null || !multiplayerClient.isConnected();
    }

    private void handleTileUpdate(String data) {
        if (currentDungeon == null || data == null || data.isEmpty()) {
            return;
        }

        String[] parts = data.split(",");
        if (parts.length < 4) {
            return;
        }

        try {
            int gridX = Integer.parseInt(parts[0]);
            int gridY = Integer.parseInt(parts[1]);
            float health = Float.parseFloat(parts[2]);
            boolean destroyed = Boolean.parseBoolean(parts[3]);

            if (gridX < 0 || gridY < 0 ||
                    gridX >= currentDungeon.getWidthTiles() ||
                    gridY >= currentDungeon.getHeightTiles()) {
                return;
            }

            Tile tile = currentDungeon.getTiles()[gridX][gridY];
            if (tile == null) {
                return;
            }

            if (destroyed) {
                tile.setType(Tile.TileType.FLOOR);
                tile.setTexture(tileTextures.get(Tile.TileType.FLOOR));
                tile.setIsCollidable(false);
            } else {
                tile.setHealth(health);
                tile.updateTextureByHealth();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void sendPlayerDamageUpdate(Player damagedPlayer, float damageAmount, float newHealth, boolean isAlive) {
        if (multiplayerClient == null || !multiplayerClient.isConnected()) {
            return;
        }

        int damagedPlayerId = myPlayerId;
        String damagedPlayerName = null;

        if (damagedPlayer != null) {
            damagedPlayerName = damagedPlayer.getName();
            if (damagedPlayer.getId() >= 0) {
                damagedPlayerId = damagedPlayer.getId();
            }
        }

        if (damagedPlayerName == null || damagedPlayerName.isEmpty()) {
            damagedPlayerName = "Player" + damagedPlayerId;
        }

        multiplayerClient.sendPlayerDamage(damagedPlayerId, damagedPlayerName, damageAmount, newHealth, isAlive);
    }

    private void announceSpawnStateToServer() {
        if (!isMultiplayer || multiplayerClient == null || !multiplayerClient.isConnected() || player == null) {
            return;
        }

        player.setHealth(player.getMaxHealth());
        player.setAlive(true);

        multiplayerClient.sendPlayerPosition(player.getX(), player.getY());
        sendPlayerDamageUpdate(player, 0f, player.getHealth(), true);
    }

    private void generateDungeonWithSeed(long seed) {
//        System.out.println("=========================================");
//        System.out.println("🏰 GENERATING DUNGEON WITH SEED: " + seed);
//        System.out.println("=========================================");

        try {
            currentDungeonSeed = seed;
            effectRandom = new Random(currentDungeonSeed ^ 0xBEEFL);
            if (activeEffectsById == null) {
                activeEffectsById = new HashMap<>();
            } else {
                activeEffectsById.clear();
            }
            if (effects == null) {
                effects = new ArrayList<>();
            } else {
                effects.clear();
            }
            nextEffectId = 1;

            // Textúrák betöltése
            //System.out.println("🔄 Betöltöm a textúrákat...");
            initGameplayResources();

            // Dungeon generálása
            //System.out.println("🎲 Hívom a DungeonGenerator-t...");
            currentDungeon = DungeonGenerator.generateRandomDungeonWithSeed(
                    seed, 32, tileTextures, enemyTexture, boxDamageTextures,
                    gateAnimationTextures, weaponCrateTexture, openCrateTexture,
                    emptyCrateTexture, weaponFactory, textRenderer, effectTextures,
                    teleportPadTexture
            );
            processedGateEvents.clear();
            pendingGateTriggers.clear();

            applyDifficultyToEnemies();

            // ✨ RÉSZLETES DEBUG
//            System.out.println("🎯 DUNGEON DATA:");
//            System.out.println("   - Spawn X: " + currentDungeon.getPlayerSpawnX());
//            System.out.println("   - Spawn Y: " + currentDungeon.getPlayerSpawnY());
//            System.out.println("   - Dungeon size: " + currentDungeon.getWidthTiles() + "x" + currentDungeon.getHeightTiles());
//            System.out.println("   - Tile size: " + currentDungeon.getTileSize());

            // ✨ FONTOS: PLAYER POZÍCIÓ MINDIG BEÁLLÍTÁSA (player soha nem null)
//            System.out.println("🎮 SETTING PLAYER SPAWN POSITION:");
//            System.out.println("   - Before: " + player.getX() + ", " + player.getY());

            player.setX(currentDungeon.getPlayerSpawnX());
            player.setY(currentDungeon.getPlayerSpawnY());
            player.setDungeon(currentDungeon); // ✨ FONTOS: dungeon beállítása
            player.activateSpawnProtection();
            announceSpawnStateToServer();

//            System.out.println("   - After: " + player.getX() + ", " + player.getY());

            // Collision manager
            collisionManager = new CollisionManager(currentDungeon);

            processPendingGateTriggers();

            // ✨ FONTOS: Kamera beállítása a player-re
            int dungeonWidthPixels = currentDungeon.getWidthTiles() * currentDungeon.getTileSize();
            int dungeonHeightPixels = currentDungeon.getHeightTiles() * currentDungeon.getTileSize();

//            System.out.println("📷 CAMERA SETUP:");
//            System.out.println("   - Dungeon pixels: " + dungeonWidthPixels + "x" + dungeonHeightPixels);
//            System.out.println("   - Player position: " + player.getX() + ", " + player.getY());

            camera.follow(player, dungeonWidthPixels, dungeonHeightPixels);

            //System.out.println("   - Camera position after follow: " + camera.getX() + ", " + camera.getY());

            // ✨ Enemy target-ek beállítása (most már van dungeon)
            for (Enemy enemy : currentDungeon.getEnemies()) {
                enemy.setTargetPlayer(this.player);
                enemy.setShowPathDebug(this.showPathDebug);
            }

            // ✨ Boss referencia beállítása
            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy instanceof Boss) {
                    boss = enemy.clone();
                    for (Enemy normalEnemy : currentDungeon.getEnemies()) {
                        if (!(normalEnemy instanceof Boss)) {
                            this.enemy = normalEnemy.clone();
                            break;
                        }
                    }
                }
            }

            upgradeScreen.setDungeon(enemy, boss);

            //System.out.println("✅ DUNGEON GENERÁLÁS SIKERES - SEED: " + seed);

        } catch (Exception e) {
            System.err.println("❌ ERROR generating dungeon with seed " + seed);
            e.printStackTrace();
        }
    }

    private void createAndSendProjectile(Player player, float targetX, float targetY,
                                         WeaponInterface weapon, double currentTime) {
        //System.out.println("🎯 createAndSendProjectile CALLED!");
        if (!isMultiplayer || multiplayerClient == null) {
            // Singleplayer - lokális lövedék
            createLocalProjectile(player, targetX, targetY, weapon, currentTime);
            return;
        }

        // 🚨 ELLENŐRIZD, HOGY A PLAYER ÉS WEAPON LÉTEZIK
        if (player == null || weapon == null) {
            System.err.println("❌ Cannot create projectile: player or weapon is null");
            return;
        }

        // Multiplayer - küldjük a szervernek
        float startX = player.getX() + player.getWidth() / 2;
        float startY = player.getY() + player.getHeight() / 2;

        // Számítsuk ki az irányvektort
        float dx = targetX - startX;
        float dy = targetY - startY;
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy);

        // 🚨 NULLA OSZTÁS VÉDELEM
        if (magnitude == 0) {
            magnitude = 1.0f; // Default érték
        }

        float velocityX = (dx / magnitude) * RANGED_PROJECTILE_SPEED;
        float velocityY = (dy / magnitude) * RANGED_PROJECTILE_SPEED;

        float damage = weapon.getDamage();

        // Küldjük a szervernek
        multiplayerClient.sendProjectileCreate(startX, startY, velocityX, velocityY, damage);

//        System.out.println("🎯 Projectile data sent to server: " +
//                startX + "," + startY + " -> " + velocityX + "," + velocityY);
    }

    private void sendEnemyUpdatesToServer() {
        if (!isMultiplayer || multiplayerClient == null || currentDungeon == null) return;

        for (Enemy enemy : currentDungeon.getEnemies()) {
            if (enemy.isAlive()) {
                // Küldjük el az enemy állapotát a szervernek
                String enemyData = String.format(Locale.US, "%d,%.2f,%.2f,%.2f,%s,%.2f,%b",
                        enemy.getId(),
                        enemy.getX(),
                        enemy.getY(),
                        enemy.getHealth(),
                        enemy.isAlive(),
                        enemy.getAttackDamage(),
                        enemy.isCritIndicatorActive());

                multiplayerClient.sendTCPMessage("ENEMY_UPDATE:" + enemyData);
            }
        }
    }

    private Enemy findEnemyById(int enemyId) {
        if (currentDungeon == null) return null;

        for (Enemy enemy : currentDungeon.getEnemies()) {
            if (enemy.getId() == enemyId) {
                return enemy;
            }
        }
        return null;
    }

    private void createLocalProjectile(Player player, float targetX, float targetY,
                                       WeaponInterface weapon, double currentTime) {
        Projectile newProjectile = weapon.shoot(player, player.getX(), player.getY(),
                targetX, targetY, (float) currentTime);
        if (newProjectile != null) {
        	applyProjectileTexture(newProjectile);
            projectiles.add(newProjectile);
            player.startAttackAnimation();
        }
    }
    
    private void applyProjectileTexture(Projectile projectile) {
        if (projectile != null && projectileTexture != null) {
            projectile.setTexture(projectileTexture);
        }
    }


    private ProjectileState deserializeProjectileState(String data) {
//        System.out.println("🔍 ===== DESERIALIZE PROJECTILE STATE =====");
//        System.out.println("🔍 Input data: '" + data + "'");

        try {
            // ✨ UGYANAZ, MINT A handlePlayerPositionUpdate-BEN!
            String[] parts = data.split(",");

            //System.out.println("🔍 Split into " + parts.length + " parts:");
            for (int i = 0; i < parts.length; i++) {
                System.out.println("   [" + i + "] = '" + parts[i] + "'");
            }

            // ✨ EGYSZERŰ 8 MEZŐS FORMÁTUM - PONTOSAN UGYANÚGY, MINT A POZÍCIÓKNÁL
            if (parts.length >= 8) {
                int projectileId = Integer.parseInt(parts[0]);
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float velocityX = Float.parseFloat(parts[3]);
                float velocityY = Float.parseFloat(parts[4]);
                int ownerPlayerId = Integer.parseInt(parts[5]);
                float damage = Float.parseFloat(parts[6]);
                boolean isActive = Boolean.parseBoolean(parts[7]);

                ProjectileState state = new ProjectileState(projectileId, x, y, velocityX, velocityY, ownerPlayerId, damage);
                state.setActive(isActive);

//                System.out.println("✅ SUCCESS - ProjectileState created:");
//                System.out.println("   ID: " + projectileId);
//                System.out.println("   Position: " + x + ", " + y);
//                System.out.println("   Velocity: " + velocityX + ", " + velocityY);
//                System.out.println("   Owner: " + ownerPlayerId);
//                System.out.println("   Damage: " + damage);
//                System.out.println("   Active: " + isActive);

                return state;
            } else {
                System.err.println("❌ Insufficient parts: " + parts.length + " (need 8)");
                return null;
            }

        } catch (Exception e) {
            System.err.println("❌ Error in deserializeProjectileState: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private EnemyState deserializeEnemyState(String data) {
//        System.out.println("🔍 ===== DESERIALIZE ENEMY STATE =====");
//        System.out.println("🔍 Input data: '" + data + "'");

        try {
            String[] parts = data.split(",");
            //System.out.println("🔍 Split into " + parts.length + " parts:");

            for (int i = 0; i < parts.length; i++) {
                System.out.println("   [" + i + "] = '" + parts[i] + "'");
            }

            // ✨ 8 MEZŐS FORMÁTUM - PONTOSAN UGYANÚGY
            if (parts.length >= 8) {
                int enemyId = Integer.parseInt(parts[0]);
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float health = Float.parseFloat(parts[3]);
                float maxHealth = Float.parseFloat(parts[4]);
                boolean isAlive = Boolean.parseBoolean(parts[5]);
                float damage = Float.parseFloat(parts[6]);
                String enemyType = parts[7];

                EnemyState state = new EnemyState(enemyId, enemyType, x, y, health, maxHealth);
                state.setAlive(isAlive);
                state.setDamage(damage);

//                System.out.println("✅ SUCCESS - EnemyState created:");
//                System.out.println("   ID: " + enemyId);
//                System.out.println("   Position: " + x + ", " + y);
//                System.out.println("   Health: " + health + "/" + maxHealth);
//                System.out.println("   Alive: " + isAlive);
//                System.out.println("   Damage: " + damage);
//                System.out.println("   Type: " + enemyType);

                return state;
            } else {
                System.err.println("❌ Insufficient parts: " + parts.length + " (need 8)");
                return null;
            }

        } catch (Exception e) {
            System.err.println("❌ Error in deserializeEnemyState: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void handleProjectileCreated(String data) {
        try {
            //System.out.println("🎯 Handling projectile creation: " + data);

            // ✨ ALTERNATÍVA: Manuális deserializáció
            ProjectileState projectileState = deserializeProjectileState(data);
            if (projectileState == null) {
                System.err.println("❌ Failed to deserialize projectile state");
                return;
            }

            // Ha a lövedék a saját játékosunké, akkor már lokálisan is létrehoztuk
            if (projectileState.getOwnerPlayerId() == myPlayerId) {
                System.out.println("🔒 Own projectile - already handled locally");
                return;
            }

            // Más játékos lövedékének létrehozása
            Player owner = otherPlayers.get(projectileState.getOwnerPlayerId());
            if (owner == null) {
                System.out.println("⚠️ Unknown projectile owner: " + projectileState.getOwnerPlayerId());
                owner = player; // Fallback
            }

            createSyncedProjectile(projectileState, owner);

        } catch (Exception e) {
            System.err.println("❌ Error handling projectile creation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createSyncedProjectile(ProjectileState projectileState, Player owner) {
        // Lövedék létrehozása a ProjectileState alapján
        float speed = (float) Math.sqrt(
                projectileState.getVelocityX() * projectileState.getVelocityX() +
                        projectileState.getVelocityY() * projectileState.getVelocityY()
        );
        float dirX = speed > 0 ? projectileState.getVelocityX() / speed : 0;
        float dirY = speed > 0 ? projectileState.getVelocityY() / speed : 0;

        Projectile projectile = new Projectile(
                projectileState.getX(),
                projectileState.getY(),
                10, 10, // width, height
                projectileState.getDamage(),
                speed,
                dirX,
                dirY,
                owner
        );

        projectile.setId(projectileState.getProjectileId());
        projectile.setAlive(projectileState.isActive());
        applyProjectileTexture(projectile);

        // Hozzáadás a szinkronizált lövedékekhez
        syncedProjectiles.put(projectileState.getProjectileId(), projectile);
        projectiles.add(projectile);

//        System.out.println("✅ Synced projectile created: ID=" + projectileState.getProjectileId() +
//                " from player " + owner.getName());
    }

    private void createSyncedProjectile(int projectileId, float x, float y,
                                        float velocityX, float velocityY, float damage, Player owner) {
        // Lövedék létrehozása
        float speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        float dirX = speed > 0 ? velocityX / speed : 0;
        float dirY = speed > 0 ? velocityY / speed : 0;

        Projectile projectile = new Projectile(x, y, 10, 10, damage, speed, dirX, dirY, owner);
        projectile.setId(projectileId);
        applyProjectileTexture(projectile);

        // Hozzáadás a szinkronizált lövedékekhez
        syncedProjectiles.put(projectileId, projectile);
        projectiles.add(projectile);

        //System.out.println("✅ Synced projectile created: ID=" + projectileId +
                //" from player " + owner.getName());
    }

    private void handleProjectileRemoved(String data) {
        try {
            int projectileId = Integer.parseInt(data);
            Projectile projectile = syncedProjectiles.get(projectileId);

            if (projectile != null) {
                projectile.setAlive(false);
                projectiles.remove(projectile);
                syncedProjectiles.remove(projectileId);
                //System.out.println("🗑️ Projectile removed: " + projectileId);
            }
        } catch (NumberFormatException e) {
            System.err.println("❌ Invalid projectile removal data: " + data);
        }
    }

    private void handleProjectileHit(String data) {
        try {
            String[] parts = data.split(":");
            int projectileId = Integer.parseInt(parts[0]);
            int enemyId = Integer.parseInt(parts[1]);
            float damage = Float.parseFloat(parts[2]);
            float newHealth = Float.parseFloat(parts[3]);
            boolean isAlive = Boolean.parseBoolean(parts[4]);

            // Lövedék inaktiválása
            Projectile projectile = syncedProjectiles.get(projectileId);
            if (projectile != null) {
                projectile.setAlive(false);
                projectiles.remove(projectile);
                syncedProjectiles.remove(projectileId);
            }

            // Ellenség állapot frissítése
            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy.getId() == enemyId) {
                    enemy.setHealth(newHealth);
                    enemy.setAlive(isAlive);

                    // Effekt létrehozása
                    //createHitEffect(enemy.getX(), enemy.getY());

//                    System.out.println("💥 Projectile hit: " + projectileId +
//                            " -> enemy " + enemyId + " (damage: " + damage + ")");
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling projectile hit: " + e.getMessage());
        }
    }

    private void startMultiplayerGameplay() {
        //System.out.println("🚀 Starting multiplayer gameplay...");

        if (player != null && currentDungeon != null) {
            player.setX(currentDungeon.getPlayerSpawnX());
            player.setY(currentDungeon.getPlayerSpawnY());
            player.setDungeon(currentDungeon);
        }

        player.setDungeon(currentDungeon);
        player.setWeapon(weaponFactory.createWeapon("pistol"), "pistol");
        player.setAbility(playerAbility);
        player.setName(playerName);

        // Kamera beállítása
        if (camera != null && currentDungeon != null) {
            int dungeonWidthPixels = currentDungeon.getWidthTiles() * currentDungeon.getTileSize();
            int dungeonHeightPixels = currentDungeon.getHeightTiles() * currentDungeon.getTileSize();
            camera.follow(player, dungeonWidthPixels, dungeonHeightPixels);
        }

        // Collision manager
        if (currentDungeon != null) {
            collisionManager = new CollisionManager(currentDungeon);
        }

        // Játék állapot beállítása
        currentState = GameState.GAMEPLAY;
        bossDefeated = false;

       //System.out.println("✅ Multiplayer gameplay started!");
    }

    private void updateFromGameState(String gameStateData) {
        if (!isMultiplayer) return;

        try {
            String[] sections = gameStateData.split(";");
            for (String section : sections) {
                if (section.startsWith("PLAYERS:")) {
                    updatePlayersFromServer(section.substring(8));
                } else if (section.startsWith("ENEMIES:")) {
                    updateEnemiesFromServer(section.substring(8));
                } else if (section.startsWith("PROJECTILES:")) {
                    updateProjectilesFromServer(section.substring(12));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error updating from game state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateFromFullGameState(String fullGameState) {
        //System.out.println("🔄 Updating from full game state");
        updateFromGameState(fullGameState);
    }

    private void updatePlayersFromServer(String playersData) {
        String[] playerEntries = playersData.split("\\|");
        for (String entry : playerEntries) {
            if (!entry.isEmpty()) {
                PlayerState playerState = PlayerState.deserialize(entry);
                if (playerState != null) {
                    serverPlayerStates.put(playerState.getPlayerId(), playerState);
                    if (playerState.getPlayerId() != myPlayerId) {
                        updateOtherPlayer(playerState);
                    } else {
                        syncOwnPlayer(playerState);
                    }
                }
            }
        }
    }

    private void updateOtherPlayer(PlayerState playerState) {
        Player otherPlayer = otherPlayers.get(playerState.getPlayerId());
        if (otherPlayer == null) {
            otherPlayer = createOtherPlayer(playerState);
            otherPlayers.put(playerState.getPlayerId(), otherPlayer);
           // System.out.println("👥 New player created: " + playerState.getPlayerName());
        }

        otherPlayer.setId(playerState.getPlayerId());
        otherPlayer.setHealth(playerState.getHealth());
        otherPlayer.setAlive(playerState.isAlive());

        if (playerState.getPlayerId() == hostPlayerId) {
            hostPresent = true;
            hostAlive = playerState.isAlive();
        }

        float newX = playerState.getX();
        float newY = playerState.getY();
        otherPlayer.setTargetX(newX);
        otherPlayer.setTargetY(newY);

        // Ha nagyon eltér a jelenlegi pozíciótól, azonnal ugorjunk a legfrissebb állapotra,
        // így a későbbi interpoláció simán tudja követni a mozgást.
        if (Math.abs(otherPlayer.getX() - newX) > 200f || Math.abs(otherPlayer.getY() - newY) > 200f) {
            otherPlayer.applyNetworkMovement(newX, newY, 0f);
            otherPlayer.activateSpawnProtection();
        }
    }

    private void syncOwnPlayer(PlayerState serverState) {
        player.setHealth(serverState.getHealth());
        player.setAlive(serverState.isAlive());
        if (isHost) {
            hostAlive = serverState.isAlive();
            hostPresent = true;
        }
    }

    private void interpolateOtherPlayers(float deltaTime) {
        for (Map.Entry<Integer, Player> entry : otherPlayers.entrySet()) {
            int playerId = entry.getKey();
            if (playerId == myPlayerId) continue;

            Player otherPlayer = entry.getValue();
            if (otherPlayer.hasTargetPosition()) {
                float currentX = otherPlayer.getX();
                float currentY = otherPlayer.getY();
                float targetX = otherPlayer.getTargetX();
                float targetY = otherPlayer.getTargetY();

                // Gyorsabb interpoláció
                float newX = currentX + (targetX - currentX) * 10.0f * deltaTime;
                float newY = currentY + (targetY - currentY) * 10.0f * deltaTime;

                otherPlayer.applyNetworkMovement(newX, newY, deltaTime);
            }
        }
    }

    private float interpolate(float current, float target, float factor) {
        return current + (target - current) * Math.min(factor, 1.0f);
    }

    private void updateOtherPlayerSpawnProtection(float deltaTime) {
        if (!isMultiplayer || otherPlayers.isEmpty()) {
            return;
        }

        for (Player otherPlayer : otherPlayers.values()) {
            if (otherPlayer != null) {
                otherPlayer.tickSpawnProtection(deltaTime);
            }
        }
    }

    private Player createOtherPlayer(PlayerState playerState) {
        // ✨ JAVÍTOTT: Ha van dungeon, használjuk a spawn pozíciót
        //float spawnX = currentDungeon != null ? currentDungeon.getPlayerSpawnX() : playerState.getX();
        //float spawnY = currentDungeon != null ? currentDungeon.getPlayerSpawnY() : playerState.getY();

        float spawnX = playerState.getX();
        float spawnY = playerState.getY();
        if (currentDungeon != null) {
            spawnX = currentDungeon.getPlayerSpawnX();
            spawnY = currentDungeon.getPlayerSpawnY();
        }

        Player otherPlayer = new Player(
                spawnX, spawnY,
                50, 50, playerIdleTexture, window, weaponFactory,
                tileTextures.get(Tile.TileType.FLOOR), textRenderer
        );

        otherPlayer.setName(playerState.getPlayerName());
        otherPlayer.setTargetX(playerState.getX());
        otherPlayer.setTargetY(playerState.getY());
        otherPlayer.setDungeon(currentDungeon); // ✨ FONTOS: DUNGEON BEÁLLÍTÁSA
        otherPlayer.setId(playerState.getPlayerId());
        otherPlayer.activateSpawnProtection();

        if (walkFrames != null && !walkFrames.isEmpty()) {
            otherPlayer.setSprites(new Sprite(walkFrames, 0.1f, true));
        }

        if (activeWalkFrames != null && !activeWalkFrames.isEmpty()) {
            otherPlayer.setActiveSprites(new Sprite(activeWalkFrames, 0.1f, false));
        }

        try {
            Player.Ability ability = Player.Ability.valueOf(playerState.getAbility());
            otherPlayer.setAbility(ability);
        } catch (Exception e) {
            otherPlayer.setAbility(Player.Ability.SPEED);
        }

        otherPlayer.setWeapon(weaponFactory.createWeapon("pistol"), "pistol");

        //System.out.println("✅ OTHER PLAYER CREATED: " + playerState.getPlayerName() +
                //" at " + spawnX + ", " + spawnY);

        return otherPlayer;
    }

    private void handlePlayerJoined(String data) {
        String[] parts = data.split(":");
        if (parts.length >= 3) {
            int playerId = Integer.parseInt(parts[0]);
            String playerName = parts[1];
            String ability = parts[2];
            float spawnX = 100f;
            float spawnY = 100f;
            boolean joinedPlayerIsHost = false;

            if (parts.length >= 5) {
                try {
                    spawnX = Float.parseFloat(parts[3]);
                    spawnY = Float.parseFloat(parts[4]);
                } catch (NumberFormatException ignored) {
                }
            }

            if (parts.length >= 6) {
                joinedPlayerIsHost = Boolean.parseBoolean(parts[5]);
            }

            if (joinedPlayerIsHost) {
                hostPlayerId = playerId;
                hostPresent = true;
                hostAlive = true;
            } else if (!isHost && (hostPlayerId < 0 || playerId < hostPlayerId)) {
                hostPlayerId = playerId;
                hostPresent = true;
                hostAlive = true;
            }

            //System.out.println("👥 Player joined: " + playerName + " (ID: " + playerId + ")");

            if (playerId != myPlayerId) {
                PlayerState playerState = new PlayerState(playerId, playerName, spawnX, spawnY, 100, 100);
                playerState.setAbility(ability);
                serverPlayerStates.put(playerId, playerState);
                updateWaitingPlayerCountText();

                // A másik játékos entitást csak akkor hozzuk létre,
                // ha a dungeon és a render erőforrások már biztosan készen vannak.
                if (currentDungeon != null && tileTextures != null) {
                    updateOtherPlayer(playerState);
                }
            }
        }
    }

    private void syncPendingOtherPlayers() {
        if (serverPlayerStates.isEmpty()) {
            return;
        }

        for (PlayerState playerState : serverPlayerStates.values()) {
            if (playerState != null && playerState.getPlayerId() != myPlayerId) {
                updateOtherPlayer(playerState);
            }
        }
    }

    private void handlePlayerDisconnected(String data) {
        int playerId = Integer.parseInt(data);
        //System.out.println("🔌 Player disconnected: " + playerId);

        if (otherPlayers.containsKey(playerId)) {
            otherPlayers.remove(playerId);
        }

        if (remotePlayerEffects != null) {
            remotePlayerEffects.remove(playerId);
        }
        
        serverPlayerStates.remove(playerId);
        
        if (getDetectedMultiplayerPlayerCount() < MULTIPLAYER_MAX_PLAYERS) {
            allPlayersReady = false;
        }
        
        updateWaitingPlayerCountText();

        if (playerId == hostPlayerId) {
            hostPresent = false;
            hostAlive = false;
        }

        forceEnemyRetarget(playerId);
    }

    private void handlePlayerPositionUpdate(String data) {
        try {
            String[] parts = data.split(":");
            int playerId = Integer.parseInt(parts[0]);
            String[] coords = parts[1].split(",");
            float x = Float.parseFloat(coords[0]);
            float y = Float.parseFloat(coords[1]);

            // ✨ FONTOS: SAJÁT PLAYER ELLENŐRZÉSE
            if (playerId == myPlayerId) {
                System.out.println("🔒 SAJÁT POZÍCIÓ - NEM FRISSÍTEM: " + playerId);
                return; // ← KILÉPÜNK, nem csinálunk semmit
            }

            //System.out.println("🔍 OTHER PLAYERS KEZELÉS - ID-k a map-ben:");
            for(int id : otherPlayers.keySet()){
                //System.out.println("   - ID: " + id + " (saját: " + myPlayerId + ")");
            }

            if (otherPlayers.containsKey(playerId)) {
                Player otherPlayer = otherPlayers.get(playerId);
                otherPlayer.setId(playerId);
                otherPlayer.setTargetX(x);
                otherPlayer.setTargetY(y);
                //System.out.println("✅ MÁSIK JÁTÉKOS FRISSÍTVE: " + playerId + " -> " + x + ", " + y);
            } else {
                System.out.println("👥 ÚJ MÁSIK JÁTÉKOS LÉTREHOZÁSA: " + playerId);
                createNewOtherPlayer(playerId, x, y);
            }
        } catch (Exception e) {
            System.err.println("❌ Hiba a pozíció feldolgozásában: " + data);
            e.printStackTrace();
        }
    }

    private void createNewOtherPlayer(int playerId, float x, float y) {
        // ✨ BIZTONSÁGI ELLENŐRZÉS
        if (playerId == myPlayerId) {
            System.err.println("❌ CRITICAL: Saját játékos létrehozási kísérlet - MEGAKADÁLYOZVA");
            return;
        }

        Player otherPlayer = new Player(
                x, y,
                50, 50, playerIdleTexture, window, weaponFactory,
                tileTextures.get(Tile.TileType.FLOOR), textRenderer
        );

        otherPlayer.setName("Player " + playerId);
        otherPlayer.setTargetX(x);
        otherPlayer.setTargetY(y);
        otherPlayer.setDungeon(currentDungeon);
        otherPlayer.setAbility(Player.Ability.SPEED);
        otherPlayer.setId(playerId);

        if (walkFrames != null && !walkFrames.isEmpty()) {
            otherPlayer.setSprites(new Sprite(walkFrames, 0.1f, true));
        }

        if (activeWalkFrames != null && !activeWalkFrames.isEmpty()) {
            otherPlayer.setActiveSprites(new Sprite(activeWalkFrames, 0.1f, false));
        }

        otherPlayers.put(playerId, otherPlayer);

       // System.out.println("✅ MÁSIK JÁTÉKOS LÉTREHOZVA: " + playerId + " at " + x + ", " + y);
    }

    private void handlePlayerAction(String data) {
        String[] parts = data.split(":");
        if (parts.length >= 4) {
            int playerId = Integer.parseInt(parts[0]);
            String action = parts[1];
            float x = Float.parseFloat(parts[2]);
            float y = Float.parseFloat(parts[3]);

            if (playerId != myPlayerId && otherPlayers.containsKey(playerId)) {
                Player otherPlayer = otherPlayers.get(playerId);
                switch (action) {
                    case "SHOOT":
                        // A lövedékeket a szerver PROJECTILE_CREATED üzenetei hozzák létre,
                        // itt elegendő az animációkat kezelni (ha szükséges).
                        break;
                    case "MELEE":
                        if (otherPlayer.getCurrentWeapon() instanceof MeleeWeapon) {
                            handleMeleeAttack(otherPlayer, (MeleeWeapon) otherPlayer.getCurrentWeapon(), (float) glfwGetTime());
                            //System.out.println("⚔️ Player " + playerId + " performed melee attack");
                        }
                        break;
                }
            }
        }
    }

    private void handlePlayerDamageUpdate(String data) {
        try {
            String[] parts = data.split(":");
            if (parts.length < 5) {
                System.err.println("❌ Invalid PLAYER_DAMAGE data: " + data);
                return;
            }

            int damagedPlayerId = Integer.parseInt(parts[0]);
            String damagedPlayerName = parts[1];
            float damageAmount = Float.parseFloat(parts[2]);
            float newHealth = Float.parseFloat(parts[3]);
            boolean isAlive = Boolean.parseBoolean(parts[4]);

            // ✨ FONTOS: MINDEN damage üzenetet dolgozz fel, még a sajátodat is!
            System.out.println("🩸 CLIENT: Damage update - PlayerID: " + damagedPlayerId +
                    ", MyID: " + myPlayerId + ", Health: " + newHealth + ", Alive: " + isAlive);

            // ✨ SAJÁT JÁTÉKOS SEBZÉSE
            if (damagedPlayerId == myPlayerId) {
                System.out.println("💥 SAJÁT SEBZÉS FRISSÍTÉS: " + newHealth + " HP");

                if (player != null) {
                    if (player.hasSpawnProtection() && newHealth < player.getHealth()) {
                        System.out.println("🛡️ Ignoring damage while spawn protection is active. Reasserting spawn state.");
                        announceSpawnStateToServer();
                        return;
                    }
                    player.setHealth(newHealth);
                    player.setAlive(isAlive);

                    if (!isAlive) {
                        System.out.println("💀 SAJÁT JÁTÉKOS MEGHALT A DAMAGE UPDATE-BEN!");
                        currentState = GameState.GAME_OVER;
                        forceEnemyRetarget(damagedPlayerId);
                    }
                }
                if (isHost) {
                    hostAlive = isAlive;
                    hostPresent = true;
                }
            }
            // ✨ MÁSIK JÁTÉKOS SEBZÉSE
            else {
                Player targetPlayer = otherPlayers.get(damagedPlayerId);
                if (targetPlayer == null) {
                    targetPlayer = findOtherPlayerByName(damagedPlayerName);
                }

                if (targetPlayer != null) {
                    targetPlayer.setHealth(newHealth);
                    targetPlayer.setAlive(isAlive);
                    if (damagedPlayerId == hostPlayerId) {
                        hostAlive = isAlive;
                        hostPresent = true;
                    }
                    System.out.println("👥 MÁSIK JÁTÉKOS SEBZÉSE: " + damagedPlayerName + " - " + newHealth + " HP");
                    if (!isAlive) {
                        forceEnemyRetarget(damagedPlayerId);
                    }
                }
            }

            refreshEnemyTargets();

        } catch (Exception e) {
            System.err.println("❌ Error in handlePlayerDamageUpdate: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePlayerEliminated(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        try {
            int eliminatedPlayerId = Integer.parseInt(data.trim());
            if (eliminatedPlayerId == myPlayerId) {
                if (player != null) {
                    player.setAlive(false);
                }
            } else {
                Player eliminated = otherPlayers.get(eliminatedPlayerId);
                if (eliminated != null) {
                    eliminated.setAlive(false);
                }
            }
            forceEnemyRetarget(eliminatedPlayerId);
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleEffectSpawnMessage(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        String[] parts = data.split(":");
        if (parts.length < 4) {
            return;
        }

        try {
            int effectId = Integer.parseInt(parts[0]);
            Effect.EffectType type = Effect.EffectType.valueOf(parts[1]);
            float x = Float.parseFloat(parts[2]);
            float y = Float.parseFloat(parts[3]);

            if (activeEffectsById != null && activeEffectsById.containsKey(effectId)) {
                return;
            }

            Texture texture = effectTextures != null ? effectTextures.get(type) : null;
            Effect effect = new Effect(x, y, 32, 32, texture, type);
            registerEffect(effect, effectId);
        } catch (Exception ignored) {
        }
    }

    private void handleEffectPickupMessage(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        String[] parts = data.split(":");
        if (parts.length < 3) {
            return;
        }

        try {
            int effectId = Integer.parseInt(parts[0]);
            int playerId = Integer.parseInt(parts[1]);
            Effect.EffectType type = Effect.EffectType.valueOf(parts[2]);

            removeEffectById(effectId);

            if (playerId == myPlayerId) {
                if (!isHost) {
                    applyEffect(type);
                }
            } else {
                Player target = otherPlayers.get(playerId);
                applyEffectToPlayer(type, target);
            }
        } catch (Exception ignored) {
        }
    }

    private PlayerState findPlayerStateByName(String playerName) {
        if (playerName == null) {
            return null;
        }

        for (PlayerState playerState : serverPlayerStates.values()) {
            if (playerName.equals(playerState.getPlayerName())) {
                return playerState;
            }
        }

        return null;
    }

    private Player findOtherPlayerByName(String playerName) {
        if (playerName == null) {
            return null;
        }

        for (Player otherPlayer : otherPlayers.values()) {
            if (playerName.equals(otherPlayer.getName())) {
                return otherPlayer;
            }
        }
        return null;
    }

    private void handleEnemyUpdate(String data) {
        try {
            //System.out.println("👹 [CLIENT] Received enemy update: " + data);

            String[] parts = data.split(",");
            if (parts.length >= 7) {
                int enemyId = Integer.parseInt(parts[0]);
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float health = Float.parseFloat(parts[3]);
                boolean isAlive = Boolean.parseBoolean(parts[4]);
                float damage = Float.parseFloat(parts[5]);
                boolean critActive = Boolean.parseBoolean(parts[6]);

                Enemy targetEnemy = findEnemyById(enemyId);
                if (targetEnemy != null) {
                    // ✨ JAVÍTÁS: CSAK KLIENSEN LEGYEN NETWORK CONTROLLED!
                    if (!isHost) { // ✨ FONTOS: Host-nál NE!
                        targetEnemy.setNetworkControlled(true);
                        targetEnemy.setTargetPosition(x, y);
                    } else {
                        // ✨ Host-nál használjuk a lokális AI-t
                        targetEnemy.setNetworkControlled(false);
                    }

                    targetEnemy.setHealth(health);
                    targetEnemy.setAlive(isAlive);
                    targetEnemy.setDamage(damage);
                    targetEnemy.syncCritIndicator(critActive);

                    //System.out.println("✅ [CLIENT] Enemy " + enemyId + " updated - Network: " + (!isHost));
                } else {
                    System.out.println("⚠️ [CLIENT] Enemy not found locally: " + enemyId);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling enemy update: " + e.getMessage());
        }
    }

    private void createNetworkEnemy(int enemyId, float x, float y, float health,
                                    boolean isAlive, float damage) {
        // ✨ ÚJ ENEMY létrehozása szerver adatok alapján
        Enemy newEnemy = new Enemy(
                x, y, 50, 50, enemyTexture, health,
                textRenderer, currentDungeon, collisionManager, player
        );

        newEnemy.setId(enemyId);
        newEnemy.setHealth(health);
        newEnemy.setAlive(isAlive);
        newEnemy.setDamage(damage);
            newEnemy.setNetworkControlled(true); // ✨ FONTOS!
        newEnemy.setTargetPosition(x, y);

        currentDungeon.getEnemies().add(newEnemy);
    }

    private void createNewEnemyFromState(EnemyState enemyState) {
        //System.out.println("👹 [CLIENT] CREATING NEW ENEMY FROM STATE: " + enemyState.getEnemyId());

        try {
            Enemy newEnemy = new Enemy(
                    enemyState.getX(), enemyState.getY(),
                    50, 50, // width, height
                    enemyTexture,
                    enemyState.getMaxHealth(),
                    textRenderer,
                    currentDungeon,
                    collisionManager,
                    player // targetPlayer
            );

            newEnemy.setId(enemyState.getEnemyId());
            newEnemy.setHealth(enemyState.getHealth());
            newEnemy.setAlive(enemyState.isAlive());
            newEnemy.setDamage(enemyState.getDamage());

            if (currentDungeon != null) {
                currentDungeon.getEnemies().add(newEnemy);
                //System.out.println("✅ [CLIENT] NEW ENEMY CREATED: ID=" + enemyState.getEnemyId() +
                //        " at " + enemyState.getX() + "," + enemyState.getY());
            } else {
                System.err.println("❌ [CLIENT] Cannot add enemy - dungeon is null");
            }

        } catch (Exception e) {
            System.err.println("❌ [CLIENT] Error creating enemy from state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleEnemyCreated(String data) {
        try {
            String[] parts = data.split(",");
            int enemyId = Integer.parseInt(parts[0]);
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            float health = Float.parseFloat(parts[3]);
            boolean isAlive = Boolean.parseBoolean(parts[4]);
            float damage = Float.parseFloat(parts[5]);

            // Ellenőrizd, hogy már létezik-e
            boolean exists = false;
            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy.getId() == enemyId) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                // Új enemy létrehozása
                Enemy newEnemy = new Enemy(x, y, 50, 50, enemyTexture, health,
                        textRenderer, currentDungeon, collisionManager, player);

                newEnemy.setId(enemyId);
                newEnemy.setHealth(health);
                newEnemy.setAlive(isAlive);
                newEnemy.setDamage(damage);
                newEnemy.setNetworkControlled(true); // FONTOS!

                currentDungeon.getEnemies().add(newEnemy);
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling enemy creation: " + e.getMessage());
        }
    }

    private void updateEnemiesFromServer(String enemiesData) {
        if (currentDungeon == null || enemiesData == null || enemiesData.isEmpty()) {
            return;
        }

        String[] enemyEntries = enemiesData.split("\\|");
        Set<Integer> deadEnemyIds = new HashSet<>();
        Set<Integer> seenEnemyIds = new HashSet<>();

        for (String entry : enemyEntries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            String[] parts = entry.contains(",") ? entry.split(",") : entry.split(":");
            if (parts.length < 5) {
                continue;
            }

            try {
                int enemyId = Integer.parseInt(parts[0]);
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float health = Float.parseFloat(parts[3]);
                boolean isAlive = Boolean.parseBoolean(parts[4]);

                seenEnemyIds.add(enemyId);
                if (!isAlive) {
                    deadEnemyIds.add(enemyId);
                }

                Enemy targetEnemy = null;
                for (Enemy enemy : currentDungeon.getEnemies()) {
                    if (enemy.getId() == enemyId) {
                        targetEnemy = enemy;
                        break;
                    }
                }

                if (targetEnemy != null) {
                    targetEnemy.setX(x);
                    targetEnemy.setY(y);
                    targetEnemy.setHealth(health);
                    targetEnemy.setAlive(isAlive);
                    if (!isAlive) {
                        targetEnemy.setNetworkControlled(false);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (!deadEnemyIds.isEmpty()) {
            currentDungeon.getEnemies().removeIf(enemy -> deadEnemyIds.contains(enemy.getId()));
        }

        if (!seenEnemyIds.isEmpty() && isMultiplayer && !isHost) {
            currentDungeon.getEnemies().removeIf(enemy -> enemy.getId() != 0 && !seenEnemyIds.contains(enemy.getId()));
        }
    }

    private void updateProjectilesFromServer(String projectilesData) {
        String[] projectileEntries = projectilesData.split("\\|");
        projectiles.clear(); // Szinkronizáljuk a lövedékeket, töröljük a helyi listát

        for (String entry : projectileEntries) {
            if (!entry.isEmpty()) {
                String[] parts = entry.split(":");
                if (parts.length >= 7) {
                    int projectileId = Integer.parseInt(parts[0]);
                    float x = Float.parseFloat(parts[1]);
                    float y = Float.parseFloat(parts[2]);
                    float velocityX = Float.parseFloat(parts[3]);
                    float velocityY = Float.parseFloat(parts[4]);
                    int ownerPlayerId = Integer.parseInt(parts[5]);
                    float damage = Float.parseFloat(parts[6]);

                    // Keresük meg a tulajdonos játékost
                    Player owner = otherPlayers.getOrDefault(ownerPlayerId, player);
                    float speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
                    float dirX = speed > 0 ? velocityX / speed : 0;
                    float dirY = speed > 0 ? velocityY / speed : 0;

                    Projectile projectile = new Projectile(
                            x, y, 10, 10, // Példa méretek
                            damage, speed, dirX, dirY, owner
                    );
                    projectile.setId(projectileId);
                    applyProjectileTexture(projectile);
                    projectiles.add(projectile);
                    //System.out.println("💥 Projectile synced from server: ID=" + projectileId + ", x=" + x + ", y=" + y);
                }
            }
        }
    }

    private void handleProjectileUpdate(String data) {
        String[] parts = data.split(":");
        if (parts.length >= 7) {
            int projectileId = Integer.parseInt(parts[0]);
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            float velocityX = Float.parseFloat(parts[3]);
            float velocityY = Float.parseFloat(parts[4]);
            int ownerPlayerId = Integer.parseInt(parts[5]);
            float damage = Float.parseFloat(parts[6]);

            for (Projectile projectile : projectiles) {
                if (projectile.getId() == projectileId) {
                    projectile.setX(x);
                    projectile.setY(y);
                    projectile.setAlive(true); // Szerver szerint aktív
                    //System.out.println("💥 Projectile updated: ID=" + projectileId + ", x=" + x + ", y=" + y);
                    return;
                }
            }

            // Ha nem találtuk meg, új lövedéket hozunk létre
            Player owner = otherPlayers.getOrDefault(ownerPlayerId, player);
            float speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            float dirX = speed > 0 ? velocityX / speed : 0;
            float dirY = speed > 0 ? velocityY / speed : 0;

            Projectile newProjectile = new Projectile(
                    x, y, 10, 10, // Példa méretek
                    damage, speed, dirX, dirY, owner
            );
            newProjectile.setId(projectileId);
            applyProjectileTexture(newProjectile);
            projectiles.add(newProjectile);
            //System.out.println("💥 New projectile created from server: ID=" + projectileId + ", x=" + x + ", y=" + y);
        }
    }

    public void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (currentState == GameState.GAMEPLAY && System.currentTimeMillis() % 5000 < 16) {
//            System.out.println("=== RENDER DEBUG ===");
//            System.out.println("State: GAMEPLAY");
//            System.out.println("Player: " + (player != null ? "OK" : "NULL"));
//            System.out.println("Weapon: " + (player != null && player.getCurrentWeapon() != null ?
//                    player.getCurrentWeapon().getClass().getSimpleName() : "NULL"));
//            System.out.println("Projectiles: " + projectiles.size());
//            System.out.println("Multiplayer: " + isMultiplayer);
//            System.out.println("====================");
        }

        if (currentState == GameState.ABILITY_SELECTION) {
            abilitySelectionScreen.render();
        } else if (currentState == GameState.LOBBY && isMultiplayer) {
            updateWaitingPlayerCountText();

            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, width, 0, height, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            if (waitingMessageRenderer != null) {
                float msgX = (width - waitingMessageRenderer.getWidth()) / 2.0f;
                float msgY = (height / 2.0f) + 20.0f;
                waitingMessageRenderer.render(msgX, msgY, 1.0f);
            }

            if (waitingPlayerCountRenderer != null) {
                float countX = (width - waitingPlayerCountRenderer.getWidth()) / 2.0f;
                float countY = (height / 2.0f) - 24.0f;
                waitingPlayerCountRenderer.render(countX, countY, 1.0f);
            }
        } else if (currentState == GameState.GAMEPLAY) {
            if (isMultiplayer) {
                // ✨ DEBUG: Mindig mutasd, kik vannak az otherPlayers-ben
                //System.out.println("🎮 RENDER DEBUG - Other players: " + otherPlayers.size() + ", Saját ID: " + myPlayerId);
                for (Map.Entry<Integer, Player> entry : otherPlayers.entrySet()) {
                    boolean isSelf = entry.getKey() == myPlayerId;
                   //System.out.println("   - ID: " + entry.getKey() +
                            //(isSelf ? " ⛔ SAJÁT - EZT NEM KELLENE LÁTNI!" : " ✅ MÁSIK"));
                }

                for (Player otherPlayer : otherPlayers.values()) {
                    if (otherPlayer.isAlive()) {
                        otherPlayer.render();
                    }
                }
            }

            glEnable(GL_TEXTURE_2D);

            camera.applyTransform();

            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            glMatrixMode(GL_MODELVIEW);

            renderTestSquare();

            if (mapRenderer != null && currentDungeon != null) {
                mapRenderer.render(currentDungeon);
            } else {
                System.err.println("❌ RENDER ERROR: MapRenderer or Dungeon is NULL!");
            }

            if (isMultiplayer) {
                for (Player otherPlayer : otherPlayers.values()) {
                    if (otherPlayer.isAlive()) {
                        otherPlayer.render();
                    }
                }
            }

            if (currentDungeon != null) {
                for (Enemy enemy : currentDungeon.getEnemies()) {
                    enemy.render();
                }
            }

            if (player != null) {
                player.render();
            }

            for (Projectile projectile : projectiles) {
                projectile.render();
            }

            for (Effect effect : effects) {
                effect.render();
            }

            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glLoadIdentity();
            glOrtho(0, width, height, 0, -1, 1);

            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glLoadIdentity();

            if (hud != null && player != null) {
                hud.render(player, playerEffects);
            }

            glPopMatrix();
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
        } else if (currentState == GameState.UPGRADE_CHOICE) {
            upgradeScreen.render();
        } else if (currentState == GameState.GAME_OVER) {
            if (gameOverScreen != null) {
                gameOverScreen.render();
            }
        }
    }

    private void renderTestSquare() {
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.0f, 1.0f, 0.0f, 1.0f); // Zöld
        glBegin(GL_QUADS);
        glVertex2f(50, 50);
        glVertex2f(100, 50);
        glVertex2f(100, 100);
        glVertex2f(50, 100);
        glEnd();
        glEnable(GL_TEXTURE_2D);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ✨ ÚJ: Fallback háttér renderelése
    private void renderFallbackBackground() {
        //System.out.println("🔄 Fallback background rendering...");

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Piros háttér - így látod, hogy valami nem stimmel
        glColor4f(0.8f, 0.2f, 0.2f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(width, 0);
        glVertex2f(width, height);
        glVertex2f(0, height);
        glEnd();

        // Fehér szöveg
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glRasterPos2f(width/2 - 100, height/2);
        // Itt jönne a szöveg renderelés, ha van text renderer-ed

        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void handleUpgradeChoice() {
        if (upgradeScreen.isChoiceMade()) {
            int choice = upgradeScreen.getSelectedOption();
            switch (choice) {
                case 0:
                    player.addHealthBoost(20f);
                    break;
                case 1:
                    player.addDamageBoost(0.1f);
                    break;
                case 2:
                    player.addCritChanceBoost(0.05f);
                    break;
            }

            if (!upgradeScreen.isGameSaved()) {
                GameSaveHandler.savePlayer(player);
                //System.out.println("✓ Automatikus mentés upgrade után");
            }

            upgradeScreen.reset();
            currentState = GameState.GAMEPLAY;
            loadNextLevel();
        }
    }

    private void handleGameOver() {
        if (gameOverScreen == null) {
            gameOverScreen = new GameOverScreen(window, width, height, player, currentDungeon);
        }
        gameOverScreen.update();

        if (gameOverScreen.isSaveGame()) {
            gameOverScreen.resetSaveFlag();
            //System.out.println("Játék mentése kérése feldolgozva");
        }

        if (gameOverScreen.isReturnToLobby()) {
            cleanupGameResources();
            if (gameOverScreen != null) {
                gameOverScreen.cleanup();
                gameOverScreen = null;
            }
            glfwSetWindowShouldClose(window, true);
            return;
        }
    }

    private void cleanupGameResources() {
        if (playerIdleTexture != null) {
            playerIdleTexture.delete();
            playerIdleTexture = null;
        }

        if (walkFrames != null) {
            for (Texture t : walkFrames) {
                if (t != null) t.delete();
            }
            walkFrames.clear();
        }

        if (activeWalkFrames != null) {
            for (Texture t : activeWalkFrames) {
                if (t != null) t.delete();
            }
            activeWalkFrames.clear();
        }

        if (tileTextures != null) {
            for (Texture texture : tileTextures.values()) {
                if (texture != null) texture.delete();
            }
            tileTextures.clear();
        }

        if (enemyTexture != null) {
            enemyTexture.delete();
            enemyTexture = null;
        }

        if (boxDamageTextures != null) {
            for (Texture texture : boxDamageTextures.values()) {
                if (texture != null) texture.delete();
            }
            boxDamageTextures.clear();
        }

        if (gateAnimationTextures != null) {
            for (Texture texture : gateAnimationTextures.values()) {
                if (texture != null) texture.delete();
            }
            gateAnimationTextures.clear();
        }

        if (effectTextures != null) {
            for (Texture texture : effectTextures.values()) {
                if (texture != null) texture.delete();
            }
            effectTextures.clear();
        }

        if (teleportPadTexture != null) {
            teleportPadTexture.delete();
            teleportPadTexture = null;
        }

        if (textRenderer != null) {
            textRenderer.cleanup();
            textRenderer = null;
        }
        
        if (waitingMessageRenderer != null) {
            waitingMessageRenderer.cleanup();
            waitingMessageRenderer = null;
        }

        if (waitingPlayerCountRenderer != null) {
            waitingPlayerCountRenderer.cleanup();
            waitingPlayerCountRenderer = null;
        }

        if (fontTexture != null) {
            fontTexture.delete();
            fontTexture = null;
        }

        if (weaponFactory != null) {
            weaponFactory.cleanup();
            weaponFactory = null;
        }

        if (currentDungeon != null) {
            currentDungeon.cleanup();
            currentDungeon = null;
        }

        if (projectiles != null) {
            projectiles.clear();
        }

        if (effects != null) {
            effects.clear();
        }

        if (playerEffects != null) {
            playerEffects.clear();
        }

        if (remotePlayerEffects != null) {
            remotePlayerEffects.clear();
        }
    }

    private void cleanupForNextLevel() {
        //System.out.println("🧹 Cleanup for next level - preserving player stats...");

        if (playerIdleTexture != null) {
            playerIdleTexture.delete();
            playerIdleTexture = null;
        }

        if (walkFrames != null) {
            for (Texture t : walkFrames) {
                if (t != null) t.delete();
            }
            walkFrames.clear();
        }

        if (activeWalkFrames != null) {
            for (Texture t : activeWalkFrames) {
                if (t != null) t.delete();
            }
            activeWalkFrames.clear();
        }

        if (tileTextures != null) {
            for (Texture texture : tileTextures.values()) {
                if (texture != null) texture.delete();
            }
            tileTextures.clear();
        }

        if (enemyTexture != null) {
            enemyTexture.delete();
            enemyTexture = null;
        }

        if (boxDamageTextures != null) {
            for (Texture texture : boxDamageTextures.values()) {
                if (texture != null) texture.delete();
            }
            boxDamageTextures.clear();
        }

        if (gateAnimationTextures != null) {
            for (Texture texture : gateAnimationTextures.values()) {
                if (texture != null) texture.delete();
            }
            gateAnimationTextures.clear();
        }

        if (effectTextures != null) {
            for (Texture texture : effectTextures.values()) {
                if (texture != null) texture.delete();
            }
            effectTextures.clear();
        }

        if (teleportPadTexture != null) {
            teleportPadTexture.delete();
            teleportPadTexture = null;
        }

        if (textRenderer != null) {
            textRenderer.cleanup();
            textRenderer = null;
        }

        if (fontTexture != null) {
            fontTexture.delete();
            fontTexture = null;
        }

        if (currentDungeon != null) {
            currentDungeon.cleanup();
            currentDungeon = null;
        }

        if (projectiles != null) {
            projectiles.clear();
        }

        if (effects != null) {
            effects.clear();
        }

        if (playerEffects != null) {
            playerEffects.clear();
        }

        //System.out.println("✅ Next level cleanup complete - player preserved with stats: " +
               // (player != null ? player.getMaxHealth() + " HP" : "NO PLAYER"));
    }

    private void cleanup() {
        try {
            cleanupGameResources();
            if (abilitySelectionScreen != null) abilitySelectionScreen.cleanup();
            if (upgradeScreen != null) upgradeScreen.cleanup();
            if (gameOverScreen != null) gameOverScreen.cleanup();

            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            glfwTerminate();

            GLFWErrorCallback callback = glfwSetErrorCallback(null);
            if (callback != null) {
                callback.free();
            }
        } catch (Exception e) {
            System.err.println("Hiba a cleanup során: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void loop() {
        double lastTime = glfwGetTime();
        double accumulator = 0.0;
        final double frameTime = 1.0 / 60.0;

        while (!glfwWindowShouldClose(window)) {
            if (currentState == GameState.GAMEPLAY) {
                inputHandler.update();
            }
            glfwPollEvents();

            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastTime;
            lastTime = currentTime;
            accumulator += deltaTime;

            // ✨ ÚJ: Nagyon egyszerű lövés kezelés
            handleShootingSimple(currentTime);

            while (accumulator >= frameTime) {
                update((float) frameTime, currentTime);
                accumulator -= frameTime;
            }

            render();
            glfwSwapBuffers(window);
        }
    }

    private void handleShootingSimple(double currentTime) {
        // Alapvető ellenőrzések
        if (currentState != GameState.GAMEPLAY) {
            return;
        }
        if (player == null) {
            System.err.println("❌ Nincs player!");
            return;
        }

        // Egérgomb ellenőrzése
        boolean mousePressed = inputHandler.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT);

        if (!mousePressed) {
            return; // Nincs lenyomva az egérgomb
        }

        // Fegyver ellenőrzése
        WeaponInterface weapon = player.getCurrentWeapon();
        if (weapon == null) {
            System.err.println("❌ Player-nek nincs fegyvere!");
            return;
        }

        //System.out.println("🎯 EGÉRGOMB LENYOMVA - Fegyver: " + weapon.getClass().getSimpleName());

        // Távolsági fegyver kezelése
        if (weapon instanceof Weapon) {
            handleRangedWeaponSimple(weapon, currentTime);
        }
        // Közelharci fegyver kezelése
        else if (weapon instanceof MeleeWeapon) {
            handleMeleeWeaponSimple((MeleeWeapon) weapon, currentTime);
        }
    }

    private void handleRangedWeaponSimple(WeaponInterface weapon, double currentTime) {
        // Cooldown ellenőrzése
        float timeSinceLastShot = (float) currentTime - lastRangedAttackTime;
        if (timeSinceLastShot < RANGED_COOLDOWN) {
            //System.out.println("⏳ Cooldown: " + (RANGED_COOLDOWN - timeSinceLastShot) + "s");
            return;
        }

        //System.out.println("🔫 TÁVOLSÁGI LÖVÉS MEGKEZDVE");

        try {
            // Célpont számítása
            float cursorX = (float) getCursorX();
            float cursorY = (float) getCursorY();
            float cameraX = camera.getX();
            float cameraY = camera.getY();

            float targetX = cursorX + cameraX;
            float targetY = cursorY + cameraY;

            //System.out.println("🎯 Célpont: " + targetX + ", " + targetY);

            // Lövedék létrehozása
            createAndSendProjectileSimple(player, targetX, targetY, weapon, currentTime);

            // Cooldown frissítése
            lastRangedAttackTime = (float) currentTime;

            //System.out.println("✅ LÖVÉS SIKERES");

        } catch (Exception e) {
            System.err.println("❌ HIBA a lövés során: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createAndSendProjectileSimple(Player player, float targetX, float targetY,
                                               WeaponInterface weapon, double currentTime) {
        //System.out.println("🚀 createAndSendProjectileSimple ELINDULT");

        // 1. ALAP ELLENŐRZÉSEK
        if (player == null) {
            System.err.println("❌ CRITICAL: Player NULL");
            return;
        }
        if (weapon == null) {
            System.err.println("❌ CRITICAL: Weapon NULL");
            return;
        }

        try {
            // 2. KEZDŐ POZÍCIÓ
            float startX = player.getX() + player.getWidth() / 2;
            float startY = player.getY() + player.getHeight() / 2;

            //System.out.println("📍 Kezdő pozíció: " + startX + ", " + startY);

            // 3. IRÁNY SZÁMÍTÁS
            float dx = targetX - startX;
            float dy = targetY - startY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance == 0) {
                dx = 1.0f; // Default jobbra
                dy = 0.0f;
                distance = 1.0f;
            }

            float dirX = dx / distance;
            float dirY = dy / distance;
            float speed = RANGED_PROJECTILE_SPEED;
            float damage = weapon.getDamage();

            //System.out.println("🎯 Irány: " + dirX + ", " + dirY);
            //System.out.println("💥 Sebzés: " + damage);

            // 4. SINGLEPLAYER vs MULTIPLAYER
            if (!isMultiplayer || multiplayerClient == null) {
                // SINGLEPLAYER - közvetlen lövedék létrehozás
                //System.out.println("🔫 SINGLEPLAYER LÖVÉS");

                Projectile projectile = new Projectile(
                        startX, startY,
                        10, 10, // width, height
                        damage,
                        speed,
                        dirX,
                        dirY,
                        player
                );

                applyProjectileTexture(projectile);
                projectiles.add(projectile);
                player.startAttackAnimation();

                //System.out.println("✅ Singleplayer lövedék létrehozva, összes: " + projectiles.size());

            } else {
                // MULTIPLAYER - küldés a szervernek
                //System.out.println("🌐 MULTIPLAYER LÖVÉS");

                // Küldés a szervernek
                multiplayerClient.sendProjectileCreate(startX, startY,
                        dirX * speed, dirY * speed, damage);

                // Lokális lövedék azonnali visszajelzésért
                Projectile localProjectile = new Projectile(
                        startX, startY,
                        10, 10,
                        damage,
                        speed,
                        dirX,
                        dirY,
                        player
                );

                applyProjectileTexture(localProjectile);
                projectiles.add(localProjectile);
                player.startAttackAnimation();

                //System.out.println("✅ Multiplayer lövedék elküldve és lokálisan létrehozva");
            }

        } catch (Exception e) {
            System.err.println("💥 CRITICAL ERROR in createAndSendProjectileSimple: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleMeleeWeaponSimple(MeleeWeapon weapon, double currentTime) {
        // Cooldown ellenőrzése
        float timeSinceLastAttack = (float) currentTime - lastMeleeAttackTime;
        if (timeSinceLastAttack < meleeCooldownTime) {
            return;
        }

        //System.out.println("⚔️ KÖZELHARCI TÁMADÁS");

        try {
            handleMeleeAttack(player, weapon, (float) currentTime);
            lastMeleeAttackTime = (float) currentTime;
            player.startAttackAnimation();

            //System.out.println("✅ KÖZELHARCI TÁMADÁS SIKERES");

        } catch (Exception e) {
            System.err.println("❌ HIBA a közelharci támadás során: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleMeleeAttack(Player player, MeleeWeapon weapon, float currentTime) {
        float timeSinceLastAttack = currentTime - lastMeleeAttackTime;

        if (timeSinceLastAttack >= meleeCooldownTime) {
            float attackRange = weapon.getRange();
            boolean hitSomething = false;

            float playerCenterX = player.getX() + player.getWidth() / 2;
            float playerCenterY = player.getY() + player.getHeight() / 2;

            List<Enemy> hitEnemies = new ArrayList<>();

            for (Enemy enemy : currentDungeon.getEnemies()) {
                float enemyCenterX = enemy.getX() + enemy.getWidth() / 2;
                float enemyCenterY = enemy.getY() + enemy.getHeight() / 2;
                float distance = (float) Math.sqrt(
                        Math.pow(playerCenterX - enemyCenterX, 2) +
                                Math.pow(playerCenterY - enemyCenterY, 2)
                );
                if (distance < attackRange && enemy.isAlive()) {
                    hitEnemies.add(enemy);
                }
            }

            for (Enemy enemy : hitEnemies) {
                float finalDamage = player.calculateFinalDamage(weapon.getDamage(), enemy);
                enemy.takeDamage(finalDamage);
                notifyEnemyDamage(enemy, finalDamage);
            }

            if (hasSharedWorldAuthority()) {
                int tileSize = currentDungeon.getTileSize();
                for (int x = 0; x < currentDungeon.getWidthTiles(); x++) {
                    for (int y = 0; y < currentDungeon.getHeightTiles(); y++) {
                        Tile tile = currentDungeon.getTiles()[x][y];
                        if (tile != null && tile.getType() == Tile.TileType.BOX) {
                            float tileCenterX = tile.getX() + tileSize / 2;
                            float tileCenterY = tile.getY() + tileSize / 2;
                            float distance = (float) Math.sqrt(
                                    Math.pow(playerCenterX - tileCenterX, 2) +
                                            Math.pow(playerCenterY - tileCenterY, 2)
                            );

                            if (distance < attackRange) {
                                tile.takeDamage(weapon.getDamage());
                                boolean destroyed = tile.isDestroyed();

                                Effect spawnedEffect = null;
                                if (destroyed) {
                                    tile.setType(Tile.TileType.FLOOR);
                                    tile.setTexture(tileTextures.get(Tile.TileType.FLOOR));
                                    tile.setIsCollidable(false);
                                    spawnedEffect = spawnRandomEffect(tile.getX(), tile.getY());
                                } else {
                                    tile.updateTextureByHealth();
                                }

                                if (isMultiplayer && multiplayerClient != null && multiplayerClient.isConnected()) {
                                    sendTileStateUpdate(x, y, tile.getHealth(), destroyed);
                                }

                                if (spawnedEffect != null && isMultiplayer && isHost && multiplayerClient != null && multiplayerClient.isConnected()) {
                                    broadcastEffectSpawn(spawnedEffect);
                                }
                                hitSomething = true;
                            }
                        }
                    }
                }
            }

            if (hitSomething) {
                //System.out.println("DEBUG: Közelharci támadás sikeres!");
            }

            lastMeleeAttackTime = currentTime;
        } else {
            //System.out.println("DEBUG: Közelharci fegyver cooldownon van.");
        }
    }

    private Effect spawnRandomEffect(float x, float y) {
        if (isMultiplayer && !isHost) {
            return null;
        }
        ensureEffectRandom();

        if (effectRandom.nextFloat() < 0.4f) {
            Effect.EffectType[] availableTypes = Effect.EffectType.values();
            if (availableTypes.length == 0) {
                return null;
            }
            Effect.EffectType type = availableTypes[effectRandom.nextInt(availableTypes.length)];
            Texture texture = effectTextures != null ? effectTextures.get(type) : null;

            Effect effect = new Effect(x, y, 32, 32, texture, type);
            return registerEffect(effect);
        }

        return null;
    }

    private void ensureEffectRandom() {
        if (effectRandom == null) {
            effectRandom = new Random();
        }
    }

    private Effect registerEffect(Effect effect) {
        return registerEffect(effect, -1);
    }

    private Effect registerEffect(Effect effect, int explicitId) {
        if (effect == null) {
            return null;
        }

        if (effects == null) {
            effects = new ArrayList<>();
        }

        if (activeEffectsById == null) {
            activeEffectsById = new HashMap<>();
        }

        if (explicitId > 0) {
            effect.setId(explicitId);
            nextEffectId = Math.max(nextEffectId, explicitId + 1);
        } else {
            effect.setId(nextEffectId++);
        }

        activeEffectsById.put(effect.getId(), effect);
        effects.add(effect);
        return effect;
    }

    private void broadcastEffectSpawn(Effect effect) {
        if (!isMultiplayer || !isHost || multiplayerClient == null || !multiplayerClient.isConnected() || effect == null) {
            return;
        }

        String message = String.format(Locale.US, "EFFECT_SPAWN:%d:%s:%.1f:%.1f",
                effect.getId(),
                effect.getType().name(),
                effect.getX(),
                effect.getY());
        multiplayerClient.sendTCPMessage(message);
    }

    private void sendEffectPickup(int effectId, int playerId, Effect.EffectType type) {
        if (!isMultiplayer || multiplayerClient == null || !multiplayerClient.isConnected() || type == null) {
            return;
        }

        String message = String.format(Locale.US, "EFFECT_PICKUP:%d:%d:%s", effectId, playerId, type.name());
        multiplayerClient.sendTCPMessage(message);
    }

    private void removeEffectById(int effectId) {
        if (activeEffectsById == null) {
            if (effects != null) {
                effects.removeIf(effect -> effect.getId() == effectId);
            }
            return;
        }

        Effect effect = activeEffectsById.remove(effectId);
        if (effect != null) {
            effect.isCollected = true;
            if (effects != null) {
                effects.remove(effect);
            }
        }
    }

    private void applyEffectToPlayer(Effect.EffectType type, Player target) {
        applyEffectInternal(target, type);
    }

    private void advanceDifficultyScaling() {
        if (currentLevelIndex < 1) {
            currentLevelIndex = 1;
        }

        currentLevelIndex++;
        enemyDamageMultiplier *= 1.10f;
        enemySpeedMultiplier *= 1.05f;
        enemyPathDeviationChance = Math.max(0.05f, enemyPathDeviationChance * 0.9f);
        enemyPathDeviationRadius = Math.max(0.5f, enemyPathDeviationRadius * 0.9f);
    }

    private void applyDifficultyToEnemies() {
        if (currentDungeon == null) {
            return;
        }

        for (Enemy enemy : currentDungeon.getEnemies()) {
            enemy.applyDifficultyMultipliers(enemyDamageMultiplier, enemySpeedMultiplier);
            enemy.setPathDeviation(enemyPathDeviationChance, enemyPathDeviationRadius);
        }
    }

    private void checkEffectCollision() {
        if (effects == null || effects.isEmpty()) {
            return;
        }

        if (isMultiplayer && !isHost) {
            return;
        }

        Iterator<Effect> iterator = effects.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            if (effect.isCollected) {
                continue;
            }

            Player collector = null;

            if (player != null && collisionManager.checkCollision(player, effect)) {
                collector = player;
            } else if (isMultiplayer) {
                for (Player other : otherPlayers.values()) {
                    if (other != null && other.isAlive() && collisionManager.checkCollision(other, effect)) {
                        collector = other;
                        break;
                    }
                }
            }

            if (collector != null) {
                applyEffectToPlayer(effect.getType(), collector);
                if (activeEffectsById != null) {
                    activeEffectsById.remove(effect.getId());
                }
                effect.isCollected = true;
                iterator.remove();

                if (isMultiplayer) {
                    int collectorId = collector == player ? myPlayerId : collector.getId();
                    if (collectorId >= 0) {
                        sendEffectPickup(effect.getId(), collectorId, effect.getType());
                    }
                }
            }
        }
    }

    private void removeCollectedEffects() {
        if (effects == null || effects.isEmpty()) {
            return;
        }

        effects.removeIf(effect -> {
            if (effect.isCollected) {
                if (activeEffectsById != null) {
                    activeEffectsById.remove(effect.getId());
                }
                return true;
            }
            return false;
        });
    }

    private void applyEffect(Effect.EffectType type) {
        applyEffectInternal(player, type);
    }

    private void applyEffectInternal(Player target, Effect.EffectType type) {
        if (target == null || type == null) {
            return;
        }

        boolean isLocalPlayer = target == player;
        List<PlayerEffect> effectList = null;

        if (isLocalPlayer) {
            if (playerEffects == null) {
                playerEffects = new ArrayList<>();
            }
            effectList = playerEffects;
        } else {
            if (remotePlayerEffects == null) {
                remotePlayerEffects = new HashMap<>();
            }
            if (target.getId() >= 0) {
                effectList = remotePlayerEffects.computeIfAbsent(target.getId(), id -> new ArrayList<>());
            }
        }

        switch (type) {
            case SPEED_BOOST:
                target.setMoveSpeed(target.getBaseMoveSpeed() * 1.5f);
                if (effectList != null) {
                    effectList.add(new PlayerEffect(Effect.EffectType.SPEED_BOOST, 5.0f));
                }
                break;
            case DAMAGE_BOOST:
                if (target.getCurrentWeapon() != null) {
                    target.setDamage(target.getCurrentWeapon().getBaseDamage() * 2.0f);
                }
                if (effectList != null) {
                    effectList.add(new PlayerEffect(Effect.EffectType.DAMAGE_BOOST, 8.0f));
                }
                break;
            case HEALTH_REGEN:
                target.heal(50);
                break;
        }
    }

    private void updatePlayerEffects(float deltaTime) {
        updatePlayerEffectList(player, playerEffects, deltaTime);

        if (remotePlayerEffects == null || remotePlayerEffects.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Integer, List<PlayerEffect>>> mapIterator = remotePlayerEffects.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<Integer, List<PlayerEffect>> entry = mapIterator.next();
            Player target = otherPlayers.get(entry.getKey());
            if (target == null) {
                mapIterator.remove();
                continue;
            }

            List<PlayerEffect> effectsForPlayer = entry.getValue();
            updatePlayerEffectList(target, effectsForPlayer, deltaTime);
            if (effectsForPlayer == null || effectsForPlayer.isEmpty()) {
                mapIterator.remove();
            }
        }
    }

    private void updatePlayerEffectList(Player target, List<PlayerEffect> effectsList, float deltaTime) {
        if (target == null || effectsList == null) {
            return;
        }

        Iterator<PlayerEffect> iterator = effectsList.iterator();
        while (iterator.hasNext()) {
            PlayerEffect effect = iterator.next();
            effect.duration -= deltaTime;
            if (effect.duration <= 0f) {
            	boolean hasSameTypeStillActive = false;
                for (PlayerEffect otherEffect : effectsList) {
                    if (otherEffect != effect && otherEffect.type == effect.type && otherEffect.duration > 0f) {
                        hasSameTypeStillActive = true;
                        break;
                    }
                }
                
                switch (effect.type) {
                    case SPEED_BOOST:
                    	if (!hasSameTypeStillActive) {
                            target.setMoveSpeed(target.getBaseMoveSpeed());
                        }
                        break;
                    case DAMAGE_BOOST:
                    	if (!hasSameTypeStillActive && target.getCurrentWeapon() != null) {
                            target.setDamage(target.getCurrentWeapon().getBaseDamage());
                        }
                        break;
                    default:
                        break;
                }
                iterator.remove();
            }
        }
    }

    public GameState getCurrentState() {
        return currentState;
    }

    private double getCursorX() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xPos = stack.mallocDouble(1);
            glfwGetCursorPos(window, xPos, null);
            return xPos.get(0);
        }
    }

    private double getCursorY() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer yPos = stack.mallocDouble(1);
            glfwGetCursorPos(window, null, yPos);
            return yPos.get(0);
        }
    }

    public static void main(String[] args) {
        boolean loop = true;
        while (loop) {
            new Lobby().run();
            loop = !Lobby.shouldExitGame();
        }
    }
}