package rendering;

import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.lwjgl.opengl.GL11;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;
import static org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memAllocInt; // ÚJ IMPORT: memAllocInt hozzáadása

// Segédosztály a képfájlok betöltésére és OpenGL textúrákká alakítására
public class TextureLoader {

    // Privát konstruktor, mert ez egy statikus segédosztály
    private TextureLoader() {}

    // Kép betöltése fájlból és OpenGL textúra létrehozása
    public static Texture loadTexture(String filePath) {
        ByteBuffer imageBuffer;
        try {
            // A "Soul_Knight.res" mappából olvassuk be a fájlt
            Path path = Paths.get("res", filePath);
            if (!Files.exists(path)) {
                System.err.println("Textúra fájl nem található: " + path.toAbsolutePath());
                return null;
            }
            imageBuffer = ioResourceToByteBuffer(path.toString(), 256 * 256); // 256KB kezdeti puffer méret
        } catch (IOException e) {
            throw new RuntimeException("Textúra betöltési hiba: " + filePath, e);
        }

        // STBImage beállítások
        stbi_set_flip_vertically_on_load(true); // Flipped képek esetén hasznos (OpenGL alapértelmezetten alulról-felfelé rajzol)

        // Allokálunk IntBuffer-eket a szélességnek, magasságnak és komponensek számának
        // HASZNÁLJUK A memAllocInt() METÓDUST AZ INTBUFFER ALLOKÁLÁSÁRA
        IntBuffer w = memAllocInt(1);   // Javítva: memAlloc(1) helyett memAllocInt(1)
        IntBuffer h = memAllocInt(1);   // Javítva: memAlloc(1) helyett memAllocInt(1)
        IntBuffer comp = memAllocInt(1); // Javítva: memAlloc(1) helyett memAllocInt(1)

        // Kép betöltése a memóriába
        ByteBuffer data = stbi_load_from_memory(imageBuffer, w, h, comp, 4); // Mindig RGBA-t kérünk (4 komponens)
        if (data == null) {
            throw new RuntimeException("Kép betöltési hiba: " + filePath + ", ok: " + stbi_failure_reason());
        }

        int width = w.get(0);
        int height = h.get(0);

        // OpenGL textúra létrehozása
        int textureID = glGenTextures(); // Generál egy új textúra azonosítót
        glBindTexture(GL_TEXTURE_2D, textureID); // Bekapcsolja ezt a textúrát

        // Textúra paraméterek beállítása
        // GL_TEXTURE_MIN_FILTER: ha a textúra kisebb, mint a rajzolt felület
        // GL_TEXTURE_MAG_FILTER: ha a textúra nagyobb, mint a rajzolt felület
        // GL_LINEAR: pixelek interpolálása a simább megjelenésért
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Képadatok feltöltése a GPU-ra
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);

        // Felszabadítjuk a memóriát
        stbi_image_free(data);
        memFree(comp);
        memFree(h);
        memFree(w);
        // Itt az imageBuffer-t NEM kell felszabadítani a memFree-vel,
        // mert az ioResourceToByteBuffer() metódusban már van memFree, ha BufferUtils-t használ.
        // Ha nem, akkor szükség lehet rá, de a jelenlegi logikában a fájlcsatornák maguktól bezáródnak.
        // A BufferUtils.createByteBuffer() által létrehozott buffereket nem kell memFree-vel felszabadítani.
        // A STBImage belsőleg allokált memóriáját szabadítja fel az stbi_image_free.
        // Tehát ez a sor: memFree(imageBuffer); elhagyható, ha az ioResourceToByteBuffer BufferUtils-t használ.
        // Esetünkben az isResourceToByteBuffer BufferUtils-t használ.

        return new Texture(textureID, width, height);
    }


    public static Texture createTextureFromBuffer(int width, int height, ByteBuffer buffer) {
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        return new Texture(textureId, width, height);
    }

    // Segédmetódus a fájl beolvasására ByteBuffer-be
    private static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
        ByteBuffer buffer;

        Path path = Paths.get(resource);
        if (Files.isReadable(path)) {
            try (SeekableByteChannel fc = Files.newByteChannel(path)) {
                buffer = BufferUtils.createByteBuffer((int) fc.size() + 1);
                while (fc.read(buffer) != -1);
            }
        } else {
            // Ezt az ágat akkor használjuk, ha a fájl az JAR-on belül van (classpath-ról)
            try (
                    InputStream source = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
                    ReadableByteChannel rbc = Channels.newChannel(source)
            ) {
                buffer = BufferUtils.createByteBuffer(bufferSize);

                while (true) {
                    int bytes = rbc.read(buffer);
                    if (bytes == -1) {
                        break;
                    }
                    if (buffer.remaining() == 0) {
                        buffer = resizeBuffer(buffer, buffer.capacity() * 2);
                    }
                }
            }
        }

        buffer.flip();
        return buffer;
    }

    private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
        ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }
}
