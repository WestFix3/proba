-- 1. Ledobjuk a táblákat (opcionális, de jó gyakorlat)
drop table ENEMY_SAVES;
drop table PLAYER_SAVES; -- Elõször a gyereket dobd, aztán a szülõt!

SELECT * FROM PLAYER_SAVES;
SELECT * FROM ENEMY_SAVES;

-- 2. Létrehozzuk a Szülõ (Player) táblát
CREATE SEQUENCE player_saves_seq START WITH 1 INCREMENT BY 1; -- Ha használnád a Sequence-t
CREATE TABLE PLAYER_SAVES (
    save_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- Ez a hivatkozott oszlop
    player_name VARCHAR2(100) NOT NULL,
    ability VARCHAR2(100),
    health NUMBER,
    max_health NUMBER,
    damage_boost NUMBER,
    crit_chance_boost NUMBER,
    save_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Létrehozzuk a Gyermek (Enemy) táblát
CREATE SEQUENCE enemy_saves_seq START WITH 1 INCREMENT BY 1; -- Ha használnád a Sequence-t
CREATE TABLE ENEMY_SAVES (
    save_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- IDEGEN KULCS OSZLOP
    player_save_id NUMBER NOT NULL,
    enemy_name VARCHAR2(100),
    health NUMBER,
    damage NUMBER,
    speed NUMBER,
    is_boss NUMBER(1) DEFAULT 0,
    save_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- IDEGEN KULCS MEGKÖTÉS (REFERENCES a már létezõ PLAYER_SAVES-re)
    FOREIGN KEY (player_save_id) REFERENCES PLAYER_SAVES(save_id)
);