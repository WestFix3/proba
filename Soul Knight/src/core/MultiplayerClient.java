package core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MultiplayerClient {
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private String serverIp;
    private int serverPort;
    private boolean connected = false;
    private int playerId = -1;

    private BufferedReader tcpIn;
    private PrintWriter tcpOut;
    private BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
    private Thread tcpListenerThread;
    private Thread udpListenerThread;

    // ✨ ÚJ: Kapcsolati állapot
    private boolean connectionEstablished = false;
    private boolean waitingForPlayerId = true;

    public MultiplayerClient(String serverIp, int serverPort) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }

    public void connect() throws IOException {
        //System.out.println("🔗 KAPCSOLÓDÁS: " + serverIp + ":" + serverPort);

        tcpSocket = new Socket(serverIp, serverPort);
        tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
        tcpOut = new PrintWriter(tcpSocket.getOutputStream(), true);

        udpSocket = new DatagramSocket();
        connected = true;
        connectionEstablished = true;

        // ✨ ELŐBB indítsd a listener-eket, UTÁNA küldj bármit
        startTCPListener();
        startUDPListener();

        //System.out.println("✅ KAPCSOLAT LÉTREJÖTT, várom a PLAYER_ID-t...");
    }

    private void startTCPListener() {
        tcpListenerThread = new Thread(() -> {
            try {
                String message;
                while (connected && (message = tcpIn.readLine()) != null) {
                    System.out.println("📨 TCP FROM SERVER: " + message);
                    receivedMessages.offer(message);

                    // ✨ FELDOLGOZZUK A SZERVER ÜZENETEKET
                    processServerMessage(message);
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("❌ TCP listener error: " + e.getMessage());
                }
            } finally {
                disconnect();
            }
        });
        tcpListenerThread.setDaemon(true);
        tcpListenerThread.start();
    }

    private void startUDPListener() {
        udpListenerThread = new Thread(() -> {
            byte[] buffer = new byte[1024];

            while (connected && !udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    //System.out.println("📨 UDP FROM SERVER: " + message);

                    // ✨ CSAK BEleteszi a queue-ba, a GameManager fogja feldolgozni
                    receivedMessages.offer(message);

                } catch (SocketTimeoutException e) {
                    // Timeout ok, folytatjuk
                } catch (IOException e) {
                    if (connected) {
                        System.err.println("❌ UDP listener error: " + e.getMessage());
                    }
                }
            }
        });
        udpListenerThread.setDaemon(true);
        udpListenerThread.start();
    }

    // ✨ ÚJ: Szerver üzenetek feldolgozása
    private void processServerMessage(String message) {
        //System.out.println("📨 TCP FROM SERVER: " + message);
        receivedMessages.offer(message);
    }

    // ✨ ÚJ: DUNGEON_SEED feldolgozása
    private void handleDungeonSeed(String seedData) {
        try {
            long seed = Long.parseLong(seedData);

        } catch (NumberFormatException e) {
            System.err.println("❌ Hibás DUNGEON_SEED formátum: " + seedData);
        }
    }

    // ✨ ÚJ: Player ID kezelése
    private void handlePlayerId(String playerIdStr) {
        try {
            this.playerId = Integer.parseInt(playerIdStr);
            //System.out.println("🎮 KAPOTT PLAYER_ID: " + this.playerId);

            // ✨ REGISZTRÁLJUK AZ UDP-T
            registerUDP();

            // ✨ ÁLLÍTSD BE A VÁRAKOZÁSI ÁLLAPOTOT
            waitingForPlayerId = false;

            //System.out.println("✅ Player ID sikeresen beállítva: " + this.playerId);

        } catch (NumberFormatException e) {
            System.err.println("❌ Érvénytelen Player ID: " + playerIdStr);
        }
    }

    // ✨ JAVÍTOTT: Join game küldése
    public void sendJoinGame(String playerName, String ability, boolean showPathDebug) {
        if (!connected || playerId == -1) {
            System.err.println("❌ Még nincs kapcsolat vagy Player ID, nem lehet JOIN-t küldeni");
            return;
        }

        String playerData = playerName + ":" + ability + ":" + showPathDebug;
        sendTCPMessage("JOIN_GAME:" + playerData);
        //System.out.println("📤 JOIN_GAME elküldve: " + playerData);
    }

    public void sendPlayerInput(String inputData) {
        if (playerId != -1) {
            //sendUDPMessage(playerId + ":PLAYER_INPUT:" + inputData);
        }
    }

    public void sendPlayerPosition(float x, float y) {
        if (playerId != -1) {
            // ✨ JAVÍTÁS: explicit pont használata
            String message = String.format(Locale.US, "%d:PLAYER_POSITION:%.4f,%.4f",
                    playerId, x, y);
            sendUDPMessage(message);
            //System.out.println("📍 KÜLDÖTT POZÍCIÓ: " + message); // Debug
        }
    }

    public void sendPlayerAction(String action, String data) {
        if (playerId != -1) {
            sendTCPMessage("PLAYER_ACTION:" + playerId + ":" + action + ":" + data);
        }
    }

    public void sendTCPMessage(String message) {
        if (connected && tcpOut != null) {
            tcpOut.println(message);
            //System.out.println("📤 TCP TO SERVER: " + message);
        } else {
            System.err.println("❌ Nincs TCP kapcsolat, nem lehet üzenetet küldeni: " + message);
        }
    }

    private void sendUDPMessage(String message) {
        if (connected && udpSocket != null) {
            try {
                byte[] data = message.getBytes("UTF-8");
                InetAddress address = InetAddress.getByName(serverIp);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, serverPort + 1);
                udpSocket.send(packet);
                //System.out.println("📤 UDP TO SERVER: " + message);
            } catch (IOException e) {
                System.err.println("❌ UDP send error: " + e.getMessage());
            }
        }
    }

    // ✨ JAVÍTOTT: UDP regisztráció
    public void registerUDP() {
        if (playerId != -1) {
            sendTCPMessage("UDP_REGISTER:" + playerId + ":" + udpSocket.getLocalPort());
            //System.out.println("📡 UDP regisztrálva port: " + udpSocket.getLocalPort());
        }
    }

    public List<String> getReceivedMessages() {
        List<String> messages = new ArrayList<>();
        receivedMessages.drainTo(messages);
        return messages;
    }

    // Add hozzá a MultiplayerClient osztályba:
    public void sendProjectileCreate(float x, float y, float velocityX, float velocityY, float damage) {
        try {
            // ✨ FONTOS: Használj US locale-t a formázáshoz
            String message = String.format(java.util.Locale.US,
                    "PROJECTILE_CREATE:%.2f:%.2f:%.2f:%.2f:%.1f",
                    x, y, velocityX, velocityY, damage);

            //System.out.println("📤 [CLIENT] Sending PROJECTILE_CREATE: " + message);
            sendTCPMessage(message);

        } catch (Exception e) {
            System.err.println("❌ Error sending PROJECTILE_CREATE: " + e.getMessage());
        }
    }

    public void sendProjectileHit(int projectileId, int enemyId, float damage) {
        String message = String.format("PROJECTILE_HIT:%d:%d:%.1f",
                projectileId, enemyId, damage);
        sendTCPMessage(message);
    }

    public boolean hasMessages() {
        return !receivedMessages.isEmpty();
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public int getPlayerId() {
        return playerId;
    }

    public boolean isConnected() {
        return connected && tcpSocket != null && !tcpSocket.isClosed();
    }

    // ✨ ÚJ: Kapcsolati állapotok
    public boolean isConnectionEstablished() {
        return connectionEstablished;
    }

    public boolean isWaitingForPlayerId() {
        return waitingForPlayerId;
    }

    public boolean isReadyForGame() {
        return connected && playerId != -1 && !waitingForPlayerId;
    }

    public void disconnect() {
        if (!connected) return;

        connected = false;
        connectionEstablished = false;

        try {
            // ✨ KÜLDJÜK A DISCONNECT ÜZENETET
            if (tcpOut != null) {
                tcpOut.println("DISCONNECT:" + playerId);
            }

            if (tcpSocket != null && !tcpSocket.isClosed()) {
                tcpSocket.close();
            }
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }
        } catch (IOException e) {
            System.err.println("❌ Error during disconnect: " + e.getMessage());
        }

        //System.out.println("🔌 Kapcsolat bontva a szerverrel");
    }
}