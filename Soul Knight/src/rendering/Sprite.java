package rendering;

import java.util.List;
import java.util.ArrayList;

/**
 * Kezeli az animációs képkockákat és a lejátszási logikát.
 */
public class Sprite {

    private List<Texture> frames;
    private int currentFrameIndex;
    private float frameDuration; // Egy képkocka megjelenítési ideje másodpercben
    private float elapsedTime;   // A lejátszás óta eltelt idő
    private boolean loop;        // Folyamatosan ismétlődjön-e az animáció
    private boolean isAnimationFinished;

    public Sprite(List<Texture> frames, float frameDuration, boolean loop) {
        this.frames = new ArrayList<>(frames);
        this.frameDuration = frameDuration;
        this.loop = loop;
        this.currentFrameIndex = 0;
        this.elapsedTime = 0.0f;
        System.out.println("DEBUG: Új Sprite létrehozva. frameDuration: " + frameDuration + ", képkockák száma: " + frames.size());
    }

    /**
     * Frissíti az animációt a delta idő alapján.
     * @param deltaTime Az utolsó frissítés óta eltelt idő másodpercben.
     */
    public void update(float deltaTime) {
        if (isFinished()) {
            return; // Ne frissítsük a sprite-ot, ha befejeződött
        }

        elapsedTime += deltaTime;
        //System.out.println("DEBUG: Sprite update() - deltaTime: " + deltaTime + ", elapsedTime: " + elapsedTime + ", frameDuration: " + frameDuration);
        if (elapsedTime >= frameDuration) {
            elapsedTime -= frameDuration;
            currentFrameIndex++;
            //System.out.println("DEBUG: Képkocka váltás! Új index: " + currentFrameIndex);

            if (currentFrameIndex >= frames.size()) {
                if (loop) {
                    currentFrameIndex = 0;
                    //System.out.println("DEBUG: Animáció ismétlődik, vissza az elejére.");
                } else {
                    currentFrameIndex = frames.size() - 1;
                    // Itt történik a befejezés jelzése
                    isAnimationFinished = true; // Hozzá kell adni egy 'isAnimationFinished' tagot
                    //System.out.println("DEBUG: Animáció véget ért, az utolsó képkockán marad.");
                }
            }
        }
    }

    /**
     * Visszaadja az aktuális képkockát.
     * @return Az animáció aktuális Texture objektuma.
     */
    public Texture getCurrentFrame() {
        if (frames.isEmpty()) {
            return null;
        }
        return frames.get(currentFrameIndex);
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    /**
     * Ellenőrzi, hogy a nem ismétlődő animáció befejeződött-e.
     * @return Igaz, ha az animáció befejeződött és nem ismétlődik.
     */
    public boolean isFinished() {
        return isAnimationFinished;
    }

    public void reset() {
        this.currentFrameIndex = 0;
        this.elapsedTime = 0.0f;
        this.isAnimationFinished = false; // Vissza kell állítani a flaget is
    }

    public void cleanup() {
        for (Texture texture : frames) {
            if (texture != null) {
                texture.delete();
            }
        }
        frames.clear();
    }
}