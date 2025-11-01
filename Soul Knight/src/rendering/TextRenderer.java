package rendering;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.GL11.*;

public class TextRenderer {

    private int textureId;
    private int width;
    private int height;

    public TextRenderer(String text, Font font, Color color) {
        createTextTexture(text, font, color);
    }

    private void createTextTexture(String text, Font font, Color color) {
        // Ideiglenes BufferedImage a szöveg méretéhez
        BufferedImage temp = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = temp.createGraphics();
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        width = fm.stringWidth(text);
        height = fm.getHeight();
        g2d.dispose();

        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }

        // Valódi BufferedImage a textúrához
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        g2d = image.createGraphics();
        g2d.setFont(font);
        g2d.setColor(color);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.drawString(text, 0, fm.getAscent());
        g2d.dispose();

        // Pixeladatok konvertálása ByteBuffer-be
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                buffer.put((byte)((pixel >> 16) & 0xFF)); // R
                buffer.put((byte)((pixel >> 8) & 0xFF));  // G
                buffer.put((byte)(pixel & 0xFF));         // B
                buffer.put((byte)((pixel >> 24) & 0xFF)); // A
            }
        }

        buffer.flip();

        // OpenGL textúra létrehozása
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void render(float x, float y, float scale) {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureId);

        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(x, y);
        glTexCoord2f(1, 1); glVertex2f(x + width * scale, y);
        glTexCoord2f(1, 0); glVertex2f(x + width * scale, y + height * scale);
        glTexCoord2f(0, 0); glVertex2f(x, y + height * scale);
        glEnd();

        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
    }
}
