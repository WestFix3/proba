package core;

import entities.Player;
import entities.Enemy;
import entities.Boss;
import entities.Player.Ability;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GameSaveHandler {

    private static final String DB_URL = "jdbc:oracle:thin:@localhost:1521:ORCL";
    private static final String DB_USER = "SYSTEM";
    private static final String DB_PASSWORD = "Asdf1234";

    public static int savePlayer(Player player) {
        // Ellenőrizzük, hogy a játékosnak van-e már mentése
        boolean hasExistingSave = checkIfPlayerHasSave(player.getName());

        if (hasExistingSave) {
            System.out.println("⚠️ A játékosnak már van mentése. Új mentés létrehozása...");
            // Itt nem töröljük a régi mentéseket, hanem új mentést hozunk létre
            return createNewSaveForPlayer(player);
        } else {
            System.out.println("✅ Új játékos, első mentés létrehozása...");
            return createNewSaveForPlayer(player);
        }
    }

    // ÚJ METÓDUS: Ellenőrzi, hogy a játékosnak van-e már mentése
    private static boolean checkIfPlayerHasSave(String playerName) {
        String sql = "SELECT COUNT(*) as save_count FROM PLAYER_SAVES WHERE player_name = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int saveCount = rs.getInt("save_count");
                return saveCount > 0;
            }

        } catch (SQLException e) {
            System.err.println("Hiba a mentések számának lekérésekor: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    // ÚJ METÓDUS: Új mentést hoz létre a játékos számára
    private static int createNewSaveForPlayer(Player player) {
        String sql = "INSERT INTO PLAYER_SAVES (player_name, ability, health, max_health, damage_boost, crit_chance_boost) VALUES (?, ?, ?, ?, ?, ?)";
        int generatedId = -1;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql, new String[]{"SAVE_ID"})) {

            pstmt.setString(1, player.getName());

            // Ellenőrizzük, hogy nem-e null az ability
            String abilityName = "None";
            if (player.getAbility() != null) {
                abilityName = player.getAbility().getDisplayName();
            }
            pstmt.setString(2, abilityName);

            pstmt.setFloat(3, player.getHealth());
            pstmt.setFloat(4, player.getMaxHealth());
            pstmt.setFloat(5, player.getDamageBoost());
            pstmt.setFloat(6, player.getCritChanceBoost());

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getInt(1);
                        System.out.println("✅ Player sikeresen mentve! ID: " + generatedId);
                    }
                }
            } else {
                System.err.println("❌ Hiba: Nem sikerült menteni a playert.");
            }

        } catch (SQLException e) {
            System.err.println("❌ Hiba a player adatbázis mentése közben: " + e.getMessage());
            e.printStackTrace();

            // Alternatív megoldás: manuálisan lekérjük a legutolsó ID-t
            generatedId = getLastInsertId();
            if (generatedId != -1) {
                System.out.println("✅ Player mentve alternatív ID-vel: " + generatedId);
            }
        }
        return generatedId;
    }

    // MÓDOSÍTOTT METÓDUS: Most már csak akkor töröl, ha explicit módon kérjük
    public static boolean deletePlayerSaves(String playerName) {
        String findSavesSQL = "SELECT save_id FROM PLAYER_SAVES WHERE player_name = ?";
        List<Integer> saveIdsToDelete = new ArrayList<>();
        boolean success = true;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(findSavesSQL)) {

            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                saveIdsToDelete.add(rs.getInt("save_id"));
            }

            // Töröljük a megtalált mentéseket
            for (int saveId : saveIdsToDelete) {
                if (!deleteSave(saveId)) {
                    success = false;
                }
            }

            if (!saveIdsToDelete.isEmpty()) {
                System.out.println("✅ " + saveIdsToDelete.size() + " régi mentés törölve a(z) " + playerName + " player számára");
            } else {
                System.out.println("ℹ️  Nincs mentés a törléshez a(z) " + playerName + " player számára");
            }

        } catch (SQLException e) {
            System.err.println("❌ Hiba a régi mentések keresése közben: " + e.getMessage());
            e.printStackTrace();
            success = false;
        }

        return success;
    }

    // ÚJ METÓDUS: Lekéri egy játékos összes mentését
    public static List<SavedGame> getPlayerSaves(String playerName) {
        List<SavedGame> savedGames = new ArrayList<>();
        String sql = "SELECT save_id, player_name, ability, health, max_health, save_date FROM PLAYER_SAVES WHERE player_name = ? ORDER BY save_date DESC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                SavedGame savedGame = new SavedGame(
                        rs.getInt("save_id"),
                        rs.getString("player_name"),
                        rs.getString("ability"),
                        rs.getFloat("health"),
                        rs.getFloat("max_health"),
                        rs.getTimestamp("save_date")
                );
                savedGames.add(savedGame);
            }

            System.out.println("✅ " + savedGames.size() + " mentés betöltve a(z) " + playerName + " játékos számára");

        } catch (SQLException e) {
            System.err.println("❌ Hiba a játékos mentéseinek listázása közben: " + e.getMessage());
            e.printStackTrace();
        }

        return savedGames;
    }

    // Alternatív metódus Oracle-hoz
    private static int getLastInsertId() {
        String sql = "SELECT PLAYER_SAVES_SEQ.CURRVAL FROM DUAL";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ Hiba a sequence lekérése közben: " + e.getMessage());
        }
        return -1;
    }

    public static void saveEnemy(Enemy enemy, int playerSaveId) {
        String sql = "INSERT INTO ENEMY_SAVES (player_save_id, enemy_name, health, damage, speed) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, playerSaveId);
            pstmt.setString(2, "Enemy");
            pstmt.setFloat(3, enemy.getHealth());
            pstmt.setFloat(4, enemy.getAttackDamage());
            pstmt.setFloat(5, enemy.getMoveSpeed());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("✅ Enemy sikeresen mentve a Player ID " + playerSaveId + " alatt!");
            } else {
                System.err.println("❌ Hiba: Nem sikerült menteni az enemy-t.");
            }

        } catch (SQLException e) {
            System.err.println("❌ Hiba az enemy adatbázis mentése közben: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void saveBoss(Boss boss, int playerSaveId) {
        String sql = "INSERT INTO ENEMY_SAVES (player_save_id, enemy_name, health, damage, speed, is_boss) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, playerSaveId);
            pstmt.setString(2, "Boss");
            pstmt.setFloat(3, boss.getHealth());
            pstmt.setFloat(4, boss.getDamage());
            pstmt.setFloat(5, boss.getMoveSpeed());
            pstmt.setBoolean(6, true);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("✅ Boss sikeresen mentve a Player ID " + playerSaveId + " alatt!");
            } else {
                System.err.println("❌ Hiba: Nem sikerült menteni a boss-t.");
            }

        } catch (SQLException e) {
            System.err.println("❌ Hiba a boss adatbázis mentése közben: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void saveGameState(Player player, Enemy currentEnemy) {
        int playerSaveId = savePlayer(player);

        if (playerSaveId == -1) {
            System.err.println("❌ Játékállapot mentési hiba: Nem sikerült a Player ID-t lekérni.");
            return;
        }

        if (currentEnemy != null) {
            if (currentEnemy instanceof Boss) {
                saveBoss((Boss) currentEnemy, playerSaveId);
            } else {
                saveEnemy(currentEnemy, playerSaveId);
            }
        }

        System.out.println("✅ Teljes játékállapot sikeresen mentve a Player ID " + playerSaveId + " alatt!");
    }

    public static Player loadPlayer(int saveId) {
        String sql = "SELECT * FROM PLAYER_SAVES WHERE save_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, saveId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String playerName = rs.getString("player_name");
                String abilityName = rs.getString("ability");
                float health = rs.getFloat("health");
                float maxHealth = rs.getFloat("max_health");
                float damageBoost = rs.getFloat("damage_boost");
                float critChanceBoost = rs.getFloat("crit_chance_boost");

                Ability ability = convertStringToAbility(abilityName);

                Player player = createPlayerWithoutDefaults();

                player.setName(playerName);
                player.setAbility(ability);
                player.setMaxHealth(maxHealth);
                player.setHealth(health);
                player.setDamageBoost(damageBoost);
                player.setCritChanceBoost(critChanceBoost);

                System.out.println("✅ Player sikeresen betöltve: " + playerName + " (HP: " + player.getHealth() + ")");
                return player;
            }

        } catch (SQLException e) {
            System.err.println("❌ Hiba a player betöltése közben: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // Segédmetódus, hogy ne kelljen a teljes konstruktort használni
    private static Player createPlayerWithoutDefaults() {
        Player player = new Player(0, 0, 50, 50, null, 0, null, null, null);
        return player;
    }

    public static Enemy loadEnemy(int playerSaveId) {
        String sql = "SELECT * FROM ENEMY_SAVES WHERE player_save_id = ? AND is_boss = 0";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, playerSaveId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                float health = rs.getFloat("health");
                float damage = rs.getFloat("damage");
                float speed = rs.getFloat("speed");

                Enemy enemy = new Enemy(0, 0, 50, 50, null, health, null, null, null, null);
                enemy.setDamage(damage);
                enemy.setMoveSpeed(speed);

                System.out.println("✅ Enemy betöltve - HP: " + health + ", DMG: " + damage + ", SPEED: " + speed);
                return enemy;
            }

        } catch (SQLException e) {
            System.err.println("❌ Hiba az enemy betöltése közben: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public static Boss loadBoss(int playerSaveId) {
        String sql = "SELECT * FROM ENEMY_SAVES WHERE player_save_id = ? AND is_boss = 1";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, playerSaveId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                float health = rs.getFloat("health");
                float damage = rs.getFloat("damage");
                float speed = rs.getFloat("speed");

                Boss boss = new Boss(0, 0, 80, 80, null, health, null, null, null, null);
                boss.setDamage(damage);
                boss.setMoveSpeed(speed);

                System.out.println("✅ Boss betöltve - HP: " + health + ", DMG: " + damage + ", SPEED: " + speed);
                return boss;
            }

        } catch (SQLException e) {
            System.err.println("❌ Hiba a boss betöltése közben: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public static List<SavedGame> listSavedGames() {
        List<SavedGame> savedGames = new ArrayList<>();
        String sql = "SELECT save_id, player_name, ability, health, max_health, save_date FROM PLAYER_SAVES ORDER BY save_date DESC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                SavedGame savedGame = new SavedGame(
                        rs.getInt("save_id"),
                        rs.getString("player_name"),
                        rs.getString("ability"),
                        rs.getFloat("health"),
                        rs.getFloat("max_health"),
                        rs.getTimestamp("save_date")
                );
                savedGames.add(savedGame);
            }

            System.out.println("✅ " + savedGames.size() + " mentett játék betöltve");

        } catch (SQLException e) {
            System.err.println("❌ Hiba a mentett játékok listázása közben: " + e.getMessage());
            e.printStackTrace();
        }

        return savedGames;
    }

    public static boolean deleteSave(int saveId) {
        String deletePlayerSQL = "DELETE FROM PLAYER_SAVES WHERE save_id = ?";
        String deleteEnemySQL = "DELETE FROM ENEMY_SAVES WHERE player_save_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            try (PreparedStatement pstmt = conn.prepareStatement(deleteEnemySQL)) {
                pstmt.setInt(1, saveId);
                pstmt.executeUpdate();
                System.out.println("✅ Hozzá tartozó enemy(k) törölve.");
            }

            try (PreparedStatement pstmt = conn.prepareStatement(deletePlayerSQL)) {
                pstmt.setInt(1, saveId);
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    System.out.println("✅ Mentés sikeresen törölve: " + saveId);
                    return true;
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Hiba a mentés törlése közben: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    private static Ability convertStringToAbility(String abilityName) {
        if (abilityName == null || abilityName.equals("None") || abilityName.trim().isEmpty()) {
            return Ability.SPEED;
        }

        switch (abilityName.toUpperCase()) {
            case "GYORSASÁG":
                return Ability.SPEED;
            case "BLOKKOLÁS":
                return Ability.BLOCK;
            case "KITÉRÉS":
                return Ability.DODGE;
            default:
                return Ability.SPEED;
        }
    }

    public static class SavedGame {
        private int saveId;
        private String playerName;
        private String ability;
        private float health;
        private float maxHealth;
        private Timestamp saveDate;

        public SavedGame(int saveId, String playerName, String ability, float health, float maxHealth, Timestamp saveDate) {
            this.saveId = saveId;
            this.playerName = playerName;
            this.ability = ability;
            this.health = health;
            this.maxHealth = maxHealth;
            this.saveDate = saveDate;
        }

        public int getSaveId() { return saveId; }
        public String getPlayerName() { return playerName; }
        public String getAbility() { return ability; }
        public float getHealth() { return health; }
        public float getMaxHealth() { return maxHealth; }
        public Timestamp getSaveDate() { return saveDate; }

        @Override
        public String toString() {
            return String.format("%s - %s (Életerő: %.0f/%.0f) - %s",
                    playerName, ability, health, maxHealth, saveDate);
        }
    }
}