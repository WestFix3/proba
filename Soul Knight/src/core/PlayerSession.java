package core;

import java.net.*;
import java.util.concurrent.*;

public class PlayerSession {
    private final int playerId;
    private final Socket clientSocket;
    private InetAddress udpAddress;
    private int udpPort;
    private boolean isHost = false;

    private String playerName;
    private String playerAbility; // String-ként tároljuk
    private boolean showPathDebug;
    private boolean ready = false;
    private String lastInput;

    private BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    public PlayerSession(int playerId, Socket clientSocket) {
        this.playerId = playerId;
        this.clientSocket = clientSocket;
    }

    // Getterek/Setterek
    public int getPlayerId() { return playerId; }
    public Socket getClientSocket() { return clientSocket; }
    public InetAddress getUdpAddress() { return udpAddress; }
    public void setUdpAddress(InetAddress udpAddress) { this.udpAddress = udpAddress; }
    public int getUdpPort() { return udpPort; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getPlayerAbility() { return playerAbility; }
    public void setPlayerAbility(String playerAbility) {
        this.playerAbility = playerAbility;
    }

    public String getPlayerAbilityAsString() {
        return playerAbility != null ? playerAbility : "SPEED";
    }

    public boolean isShowPathDebug() { return showPathDebug; }
    public void setShowPathDebug(boolean showPathDebug) { this.showPathDebug = showPathDebug; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public String getLastInput() { return lastInput; }
    public void setLastInput(String lastInput) { this.lastInput = lastInput; }

    public void addInput(String input) {
        inputQueue.offer(input);
    }

    public boolean hasPendingInput() {
        return !inputQueue.isEmpty();
    }

    public String getNextInput() {
        return inputQueue.poll();
    }

    public boolean isHost() { return isHost; }
    public void setHost(boolean host) { this.isHost = host; }

    public void clearInput() {
        inputQueue.clear();
    }
}