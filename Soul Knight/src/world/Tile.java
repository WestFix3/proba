package world;

import rendering.Texture;
import physics.Rectangle;
import java.util.Map;
import static org.lwjgl.opengl.GL11.*;

public class Tile extends Rectangle {

    public enum TileType {
        FLOOR,
        WALL,
        DESTRUCTIBLE_WALL,
        BOX,
        GATE,
        EFFECT_CRATE_EXPLOSION,
        EFFECT_GATE_DESTRUCTION,
        WEAPON_CRATE,
        SHOP_FLOOR,
        TELEPORT_PAD
    }

    private boolean isCollidable;
    private TileType type;
    private int tileSize;
    private Texture texture;
    private int gridX;
    private int gridY;

    private float health;
    private float maxHealth;
    private Map<Integer, Texture> damageTextures;

    private String weaponId = null;
    private Texture emptyTexture = null;
    private Texture openTexture = null;
    private boolean isOpen = false;

    // EGYSZERŰSÍTETT KONSTRUKTOROK
    public Tile(TileType type, int gridX, int gridY, int tileSize, Texture texture) {
        super(gridX * tileSize, gridY * tileSize, tileSize, tileSize);
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.tileSize = tileSize;
        this.texture = texture;
        this.isCollidable = setCollidableByType(type);
    }

    public Tile(TileType type, int gridX, int gridY, int tileSize, Texture weaponCrateTexture, Texture emptyCrateTexture, String weaponId) {
        super(gridX * tileSize, gridY * tileSize, tileSize, tileSize);
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.tileSize = tileSize;
        this.texture = weaponCrateTexture;
        this.isCollidable = setCollidableByType(type);
        this.weaponId = weaponId;
        this.emptyTexture = emptyCrateTexture;
    }

    public Tile(TileType type, int gridX, int gridY, int tileSize, Texture texture, float health, Map<Integer, Texture> damageTextures) {
        super(gridX * tileSize, gridY * tileSize, tileSize, tileSize);
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.tileSize = tileSize;
        this.texture = texture;
        this.health = health;
        this.maxHealth = health;
        this.damageTextures = damageTextures;
        this.isCollidable = setCollidableByType(type);
    }

    public Tile(TileType type, int gridX, int gridY, int tileSize, Texture texture, Map<Integer, Texture> gateAnimationTextures) {
        super(gridX * tileSize, gridY * tileSize, tileSize, tileSize);
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.tileSize = tileSize;
        this.texture = texture;
        this.damageTextures = gateAnimationTextures;
        this.isCollidable = setCollidableByType(type);
    }

    // Módosított konstruktor fegyverládákhoz
    public Tile(TileType type, int gridX, int gridY, int tileSize, Texture closedTexture, Texture openTexture, String weaponId, Texture emptyTexture) {
        super(gridX * tileSize, gridY * tileSize, tileSize, tileSize);
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.tileSize = tileSize;
        this.texture = closedTexture;
        this.isCollidable = setCollidableByType(type);
        this.weaponId = weaponId;
        this.openTexture = openTexture;
        this.emptyTexture = emptyTexture;
    }

    private boolean setCollidableByType(TileType type) {
        return type == TileType.WALL ||
                type == TileType.DESTRUCTIBLE_WALL ||
                type == TileType.BOX ||
                type == TileType.GATE ||
                type == TileType.WEAPON_CRATE;
    }

    public boolean isCollidable() {
        return isCollidable;
    }

    public void setIsCollidable(boolean collidable) {
        this.isCollidable = collidable;
    }

    public void updateGateAnimation(int phase) {
        if (this.type == TileType.GATE && this.damageTextures != null) {
            this.texture = this.damageTextures.get(phase);
        }
    }

    public void takeDamage(float damage) {
        if (isDestroyable()) {
            this.health -= damage;
            System.out.println("BOX HP: " + this.health + " | DMG: " + damage);
            if (this.health < 0) {
                this.health = 0;
            }
        }
    }

    public boolean isDestroyed() {
        return isDestroyable() && health <= 0;
    }

    public void updateTextureByHealth() {
        if (isDestroyable() && health > 0) {
            if (damageTextures != null) {
                if (health == 2) {
                    this.texture = damageTextures.get(1);
                } else if (health == 1) {
                    this.texture = damageTextures.get(2);
                }
            }
        }
    }

    public void render() {
        if (texture != null) {
            texture.bind();
        } else {
            glColor3f(1.0f, 0.0f, 1.0f);
        }

        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(x, y);
        glTexCoord2f(1, 0); glVertex2f(x + width, y);
        glTexCoord2f(1, 1); glVertex2f(x + width, y + height);
        glTexCoord2f(0, 1); glVertex2f(x, y + height);
        glEnd();

        if (texture != null) {
            texture.unbind();
        }
        glColor3f(1.0f, 1.0f, 1.0f);
    }

    public boolean isSolid() {
        return isCollidable;
    }

    public boolean isDestroyable() {
        return type == TileType.DESTRUCTIBLE_WALL || type == TileType.BOX;
    }

    public TileType getType() {
        return type;
    }

    public void setType(TileType type) {
        this.type = type;
        this.isCollidable = setCollidableByType(type);
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    public String getWeaponId() {
        return weaponId;
    }

    public Texture getOpenTexture() {
        return openTexture;
    }

    public Texture getEmptyTexture() {
        return emptyTexture;
    }

    public void openCrate() {
        if (this.type == TileType.WEAPON_CRATE && !this.isOpen) {
            this.isOpen = true;
            this.texture = this.openTexture;
            System.out.println("DEBUG: Láda kinyitva.");
        }
    }

    public void takeWeapon() {
        if (this.type == TileType.WEAPON_CRATE && this.isOpen && this.weaponId != null) {
            this.weaponId = null;
            this.texture = this.emptyTexture;
            System.out.println("DEBUG: Fegyver felvéve. A láda kiürült.");
        } else {
            System.out.println("DEBUG: A láda már üres vagy nincs kinyitva.");
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public float getHealth() {
        return health;
    }

    public Map<Integer, Texture> getDamageTextures() {
        return damageTextures;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public int getTileSize() {
        return tileSize;
    }

    public void cleanup() {
        // Felszabadítja a fő textúra erőforrását
        if (this.texture != null) {
            this.texture.delete();
        }
        // Felszabadítja a fegyverláda textúrák erőforrásait
        if (this.openTexture != null) {
            this.openTexture.delete();
        }
        if (this.emptyTexture != null) {
            this.emptyTexture.delete();
        }
        // Felszabadítja az animációkhoz/sebződéshez tartozó textúrákat
        if (this.damageTextures != null) {
            for (Texture tex : this.damageTextures.values()) {
                if (tex != null) {
                    tex.delete();
                }
            }
            this.damageTextures.clear();
        }
    }
}