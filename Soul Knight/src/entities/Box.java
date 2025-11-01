// entities/Box.java
package entities;

import rendering.Texture;
import physics.Rectangle; // Szükséges, ha az Entity.java-ban is használja, vagy ha itt is létrehozod a Rectangle-t
import static org.lwjgl.opengl.GL11.*;

// FONTOS VÁLTOZTATÁS: Terjeszd ki az Entity osztályt!
public class Box extends Entity { // <-- Ezt add hozzá!

    private Texture texture;
    private boolean isDestroyed;

    // A konstruktornak meg kell hívnia a super() konstruktort
    public Box(float x, float y, float width, float height, Texture texture) {
        super(x, y, width, height); // <-- Hívd meg az Entity ősosztály konstruktorát
        this.texture = texture;
        this.isDestroyed = false;
    }

    // A getterek már az ősosztályból jönnek (getX, getY, getWidth, getHeight, getBounds),
    // de megismételheted őket, ha szeretnéd, vagy speciális logikát adnál nekik.
    // De ha az Entity-ben vannak, akkor törölheted innen a duplikáltakat:
    // public float getX() { return x; }
    // public float getY() { return y; }
    // public float getWidth() { return width; }
    // public float getHeight() { return height; }

    public Texture getTexture() { return texture; }
    public boolean isDestroyed() { return isDestroyed; }

    public void setDestroyed(boolean destroyed) {
        isDestroyed = destroyed;
    }

    @Override // Fontos: felülírja az Entity absztrakt render metódusát (ha van)
    public void render() {
        if (!isDestroyed && texture != null) {
            glPushMatrix();
            // Mivel a Box renderelése középpont alapú (-width/2, -height/2),
            // és a konstruktorban valószínűleg a bal felső sarokhoz adod az x,y-t,
            // itt korrigálnod kell, vagy eldönteni, hogy az x,y a középpont.
            // Ha x,y a BAL FELSŐ SAROK, de a renderelés középpont alapú, akkor így:
            glTranslatef(x + width / 2, y + height / 2, 0); // Eltolás a középpontba

            texture.bind();
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(-width / 2, -height / 2); // Bal felső
            glTexCoord2f(1, 0); glVertex2f(width / 2, -height / 2);  // Jobb felső
            glTexCoord2f(1, 1); glVertex2f(width / 2, height / 2);   // Jobb alsó
            glTexCoord2f(0, 1); glVertex2f(-width / 2, height / 2);  // Bal alsó
            glEnd();
            texture.unbind();

            glPopMatrix();
        }
        // Ha nem rombolódott el ÉS nincs textúrája, akkor se rajzoljon.
        // VAGY rajzoljon egy fallback színt.
    }

    @Override // Fontos: felülírja az Entity absztrakt update metódusát (ha van)
    public void update(float deltaTime, Object... args) {
        // A dobozoknak valószínűleg nincs update logikájuk,
        // de ha az Entity absztrakt metódus, akkor itt kell üresen implementálni.
    }
}