package core;

import entities.Player;
import entities.Effect.PlayerEffect;
import rendering.TextRenderer;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class HUD {
    // --- Konstansok (mezők) ---
    private static final int HUD_MARGIN = 20;
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 20;
    // MÓDOSÍTVA: EFFECT_TEXT_WIDTH a keskenyebb téglalaphoz
    private static final int EFFECT_TEXT_WIDTH = 180; // Keskenyebb téglalap a 2 effecthez és "Nincs erősítés" szöveghez
    private static final int EFFECT_MARGIN = 10;
    private static final int EFFECT_TEXT_HEIGHT = 25;

    // STATISZTIKÁK MEZŐ MÉRETE (Megnövelve, hogy több sor elférjen)
    private static final int STATS_WIDTH = 180;
    private static final int STATS_HEIGHT = 75; // Hely a 3-4 sornak

    // --- Példányváltozók (mezők) ---
    private int screenWidth;
    private int screenHeight;
    private TextRenderer textRenderer;

    /**
     * Konstruktor a HUD inicializálására.
     */
    public HUD(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        // TextRenderer inicializálása
        try {
            this.textRenderer = new TextRenderer("", new Font("Arial", Font.BOLD, 14), Color.WHITE);
        } catch (Exception e) {
            System.err.println("Hiba a TextRenderer inicializálásakor: " + e.getMessage());
            this.textRenderer = null;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Rendereli a HUD-ot (életerő sáv, sebzés indikátor, effektek, statisztikák).
     */
    public void render(Player player, List<PlayerEffect> playerEffects) {
        if (player == null) {
            return;
        }

        glDisable(GL_TEXTURE_2D);

        // 1. Háttér a teljes HUD-nak (sötét szürke, átlátszó)
        glColor4f(0.1f, 0.1f, 0.1f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(screenWidth, 0);
        glVertex2f(screenWidth, 95); // Megnövelt magasság
        glVertex2f(0, 95);
        glEnd();

        renderHealthBar(player);
        renderDamageIndicator(player);
        renderPlayerStats(player);
        renderEffects(playerEffects);
        renderTextElements(player, playerEffects);

        // Szín visszaállítása
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        glEnable(GL_TEXTURE_2D);
    }

    private void renderHealthBar(Player player) {
        // 2. Életerő sáv háttér (piros)
        glColor4f(0.8f, 0.0f, 0.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(HUD_MARGIN, HUD_MARGIN);
        glVertex2f(HUD_MARGIN + BAR_WIDTH, HUD_MARGIN);
        glVertex2f(HUD_MARGIN + BAR_WIDTH, HUD_MARGIN + BAR_HEIGHT);
        glVertex2f(HUD_MARGIN, HUD_MARGIN + BAR_HEIGHT);
        glEnd();

        // 3. Életerő sáv kitöltött rész (zöld)
        float healthPercentage = player.getHealth() / player.getMaxHealth();
        glColor4f(0.0f, 0.8f, 0.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(HUD_MARGIN, HUD_MARGIN);
        glVertex2f(HUD_MARGIN + BAR_WIDTH * healthPercentage, HUD_MARGIN);
        glVertex2f(HUD_MARGIN + BAR_WIDTH * healthPercentage, HUD_MARGIN + BAR_HEIGHT);
        glVertex2f(HUD_MARGIN, HUD_MARGIN + BAR_HEIGHT);
        glEnd();

        // 4. Életerő sáv keret (fehér)
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(HUD_MARGIN, HUD_MARGIN);
        glVertex2f(HUD_MARGIN + BAR_WIDTH, HUD_MARGIN);
        glVertex2f(HUD_MARGIN + BAR_WIDTH, HUD_MARGIN + BAR_HEIGHT);
        glVertex2f(HUD_MARGIN, HUD_MARGIN + BAR_HEIGHT);
        glEnd();
        glLineWidth(1.0f);
    }

    /**
     * Visszaállítva az eredeti sebzés indikátorra: háttér + sáv szimuláció.
     */
    private void renderDamageIndicator(Player player) {
        int damageX = HUD_MARGIN + BAR_WIDTH + 20;
        int damageY = HUD_MARGIN;
        int damageBarWidth = 130;
        int damageBarHeight = BAR_HEIGHT;

        // 5. Sebzés indikátor háttér (sötét)
        glColor4f(0.3f, 0.3f, 0.3f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(damageX, damageY);
        glVertex2f(damageX + damageBarWidth, damageY);
        glVertex2f(damageX + damageBarWidth, damageY + damageBarHeight);
        glVertex2f(damageX, damageY + damageBarHeight);
        glEnd();

        // 6. Sebzés indikátor (narancs) - szimulált sáv, mely a sebzés erősségét tükrözi.
        float damageValue = (player.getCurrentWeapon() != null) ? player.getCurrentWeapon().getDamage() : player.getBaseDamage();
        float maxDamage = 50.0f; // Feltételezünk egy maximum sebzést a sáv arányosításához
        float damagePercentage = Math.min(1.0f, damageValue / maxDamage);

        glColor4f(1.0f, 0.5f, 0.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(damageX + 2, damageY + 2);
        glVertex2f(damageX + 2 + (damageBarWidth - 4) * damagePercentage, damageY + 2);
        glVertex2f(damageX + 2 + (damageBarWidth - 4) * damagePercentage, damageY + damageBarHeight - 2);
        glVertex2f(damageX + 2, damageY + damageBarHeight - 2);
        glEnd();
    }

    /**
     * Játékos statisztikáinak grafikus háttere.
     */
    private void renderPlayerStats(Player player) {
        // A Player Stats blokk pozíciója
        int statsX = HUD_MARGIN + BAR_WIDTH + 20 + 150 + EFFECT_MARGIN;
        int statsY = HUD_MARGIN;

        // Statisztika háttér (kékesszürke)
        glColor4f(0.2f, 0.2f, 0.3f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(statsX, statsY);
        glVertex2f(statsX + STATS_WIDTH, statsY);
        glVertex2f(statsX + STATS_WIDTH, statsY + STATS_HEIGHT);
        glVertex2f(statsX, statsY + STATS_HEIGHT);
        glEnd();

        // Keret (fehér)
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glLineWidth(1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(statsX, statsY);
        glVertex2f(statsX + STATS_WIDTH, statsY);
        glVertex2f(statsX + STATS_WIDTH, statsY + STATS_HEIGHT);
        glVertex2f(statsX, statsY + STATS_HEIGHT);
        glEnd();
    }

    private void renderEffects(List<PlayerEffect> playerEffects) {
        // MÓDOSÍTVA: 2 különálló slot renderelése
        int effectStartY = HUD_MARGIN + BAR_HEIGHT + 10;
        int maxEffects = 2; // Csak 2 effect téglalap

        for (int i = 0; i < maxEffects; i++) {
            int effectX = HUD_MARGIN + (i * (EFFECT_TEXT_WIDTH + EFFECT_MARGIN));
            PlayerEffect effect = (playerEffects != null && i < playerEffects.size()) ? playerEffects.get(i) : null;

            // Effect háttér
            if (effect != null) {
                glColor4f(0.3f, 0.3f, 0.3f, 0.8f);
            } else {
                // Mindig rajzoljuk a szürke hátteret, ha nincs effekt (vagy a slot üres)
                glColor4f(0.5f, 0.5f, 0.5f, 0.7f);
            }

            glBegin(GL_QUADS);
            glVertex2f(effectX, effectStartY);
            glVertex2f(effectX + EFFECT_TEXT_WIDTH, effectStartY);
            glVertex2f(effectX + EFFECT_TEXT_WIDTH, effectStartY + EFFECT_TEXT_HEIGHT);
            glVertex2f(effectX, effectStartY + EFFECT_TEXT_HEIGHT);
            glEnd();

            // Visszaszámláló sáv (csak ha van aktív effekt)
            if (effect != null) {
                switch (effect.type) {
                    case SPEED_BOOST:
                        glColor4f(0.0f, 1.0f, 0.0f, 0.9f); // Zöld
                        break;
                    case DAMAGE_BOOST:
                        glColor4f(1.0f, 0.0f, 0.0f, 0.9f); // Piros
                        break;
                    case HEALTH_REGEN:
                        glColor4f(0.0f, 0.0f, 1.0f, 0.9f); // Kék
                        break;
                    default:
                        glColor4f(1.0f, 1.0f, 1.0f, 0.9f); // Fehér
                }

                float maxDuration = 10.0f; // Feltételezett maximum idő
                float timePercentage = Math.min(1.0f, effect.duration / maxDuration);
                float barHeight = 4.0f;

                glBegin(GL_QUADS);
                glVertex2f(effectX, effectStartY + EFFECT_TEXT_HEIGHT - barHeight);
                glVertex2f(effectX + (EFFECT_TEXT_WIDTH * timePercentage), effectStartY + EFFECT_TEXT_HEIGHT - barHeight);
                glVertex2f(effectX + (EFFECT_TEXT_WIDTH * timePercentage), effectStartY + EFFECT_TEXT_HEIGHT);
                glVertex2f(effectX, effectStartY + EFFECT_TEXT_HEIGHT);
                glEnd();
            }
        }
    }

    private void renderTextElements(Player player, List<PlayerEffect> playerEffects) {
        if (textRenderer == null) {
            return;
        }

        try {
            // Életerő szövegek
            //System.out.println("KURVA ANYÁD TESZT HUD: " + String.format("%.0f/%.0f", player.getHealth(), player.getMaxHealth()));
            String healthText = String.format("%.0f/%.0f", player.getHealth(), player.getMaxHealth());
            renderText(healthText, HUD_MARGIN + 80, HUD_MARGIN + 15, Color.WHITE);
            renderText("Életerő", HUD_MARGIN + 5, HUD_MARGIN - 5, Color.WHITE);

            // Sebzés szöveg (a sáv felett)
            int damageX = HUD_MARGIN + BAR_WIDTH + 20;
            if (player.getCurrentWeapon() != null) {
                // A sebzés értékét a sebzés indikátorra írjuk
                String damageValueText = String.format("%.1f", player.getCurrentWeapon().getDamage());
                renderText(damageValueText, damageX + 50, HUD_MARGIN + 15, Color.ORANGE);
            }
            renderText("Sebzés", damageX + 5, HUD_MARGIN - 5, Color.WHITE);

            // --- JÁTÉKOS STATISZTIKÁK SZÖVEGEI (Jobb oldali blokk) ---
            int statsX = HUD_MARGIN + BAR_WIDTH + 20 + 150 + EFFECT_MARGIN;
            int currentY = HUD_MARGIN + 15; // Első sor Y pozíciója

            // 1. Sebesség (Effective Move Speed)
            float effectiveSpeed = player.getEffectiveMoveSpeed(); // <-- HASZNÁLJA AZ ÚJ PLAYERT METÓDUST
            String speedText = String.format("Sebesség: %.0f", effectiveSpeed);
            renderText(speedText, statsX + 5, currentY, Color.CYAN);
            currentY += 20;

            // 2. Kritikus Esély Növekmény
            float totalCritChance = player.getCritChance() * 100f; // <-- HASZNÁLJA AZ ÚJ PLAYERT METÓDUST
            String critText = String.format("Crit: %.0f%%", totalCritChance);
            renderText(critText, statsX + 5, currentY, Color.MAGENTA);
            currentY += 20;

            // 3. Aktív Képesség
            String abilityName = player.getAbility() != null ? player.getAbility().getDisplayName() : "Nincs Képesség";
            Color abilityColor = player.isAbilityActive() ? Color.GREEN : Color.LIGHT_GRAY;
            renderText("Képesség: " + abilityName, statsX + 5, currentY, abilityColor);
            currentY += 20;


            // Effect szövegek (Alul lévő sor)
            int effectStartY = HUD_MARGIN + BAR_HEIGHT + 10;
            int maxEffects = 2; // Csak 2 effect téglalap
            boolean hasActiveEffects = playerEffects != null && !playerEffects.isEmpty();

            if (hasActiveEffects) {
                // Ha van aktív effect, akkor a slot tartalmát írjuk ki
                for (int i = 0; i < maxEffects; i++) {
                    int effectX = HUD_MARGIN + (i * (EFFECT_TEXT_WIDTH + EFFECT_MARGIN));
                    PlayerEffect effect = (i < playerEffects.size()) ? playerEffects.get(i) : null;

                    if (effect != null) {
                        String effectName = getEffectHungarianName(effect.type);
                        String timeText = String.format("%.1fs", effect.duration);

                        Color effectColor = getEffectColor(effect.type);

                        renderText(effectName, effectX + 5, effectStartY + 15, effectColor);
                        renderText(timeText, effectX + EFFECT_TEXT_WIDTH - 30, effectStartY + 15, Color.WHITE);
                    }
                }
            } else {
                // MÓDOSÍTÁS: Ha nincs aktív effect, CSAK az első slotba írjuk ki a szöveget, fekete színnel és a megadott szöveggel
                int effectX = HUD_MARGIN;
                renderText("Nincs aktív erősítés", effectX + 5, effectStartY + 15, Color.BLACK);
            }


        } catch (Exception e) {
            System.err.println("Hiba a szöveg renderelésekor: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Visszaadja az effecthez tartozó színt (szöveghez).
     */
    private Color getEffectColor(entities.Effect.EffectType type) {
        switch (type) {
            case SPEED_BOOST:
                return Color.GREEN;
            case DAMAGE_BOOST:
                return Color.RED;
            case HEALTH_REGEN:
                return Color.BLUE;
            default:
                return Color.WHITE;
        }
    }

    /**
     * Segédmetódus a szöveg rendereléséhez. Most már fogad színt is.
     */
    private void renderText(String text, float x, float y, Color color) {
        if (textRenderer == null) {
            return;
        }

        try {
            // Új TextRenderer a megfelelő szöveggel és színnel
            TextRenderer localRenderer = new TextRenderer(text, new Font("Arial", Font.BOLD, 14), color);

            glPushAttrib(GL_ALL_ATTRIB_BITS);
            glPushMatrix();

            glDisable(GL_TEXTURE_2D);
            glTranslatef(x, y, 0);
            glScalef(1, -1, 1);
            localRenderer.render(0, 0, 1.0f);

            glPopMatrix();
            glPopAttrib();

            glEnable(GL_TEXTURE_2D);

            localRenderer.cleanup();
        } catch (Exception e) {
            System.err.println("Hiba a '" + text + "' szöveg renderelésekor: " + e.getMessage());
        }
    }

    /**
     * Effect nevek magyarul.
     */
    private String getEffectHungarianName(entities.Effect.EffectType type) {
        switch (type) {
            case SPEED_BOOST:
                return "Gyorsaság";
            case DAMAGE_BOOST:
                return "Sebzés";
            case HEALTH_REGEN:
                return "Életerő";
            default:
                return "Ismeretlen";
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Felszabadítja az erőforrásokat.
     */
    public void cleanup() {
        if (textRenderer != null) {
            textRenderer.cleanup();
        }
    }

    /**
     * Beállítja a képernyő méretét (átméretezés esetén hasznos).
     */
    public void setScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }
}