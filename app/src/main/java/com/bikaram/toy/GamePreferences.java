package com.bikaram.toy;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GamePreferences {
  static final String[] SKIN_IDS = {
    "CLASSIC", "FOOTBALL", "COCONUT", "DISCO", "WATERMELON", "MOON", "PINGPONG"
  };
  static final String[] SKIN_NAMES = {
    "Classic", "Football", "Coconut", "Disco", "Watermelon", "Moon", "Ping Pong"
  };
  static final long[] TAP_ACHIEVEMENTS = {10, 100, 1_000, 10_000, 100_000};
  private static final String FILE = "bikaram_state";
  private static final String RECORDS = "records_v2";
  private static final int MAX_RECORDS = 40;

  private final SharedPreferences prefs;
  private long totalTaps;
  private long todayTaps;
  private int bestStreak;
  private int pendingTaps;
  private String today;
  private String mode;
  private String skin;
  private boolean sound;
  private boolean haptics;
  private boolean deviceGravity;
  private boolean reducedMotion;
  private String intensity;

  GamePreferences(Context context) {
    prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    totalTaps = prefs.getLong("totalTaps", 0);
    bestStreak = prefs.getInt("bestStreak", 0);
    mode = prefs.getString("mode", "NORMAL");
    skin = prefs.getString("skin", "CLASSIC");
    sound = prefs.getBoolean("sound", true);
    haptics = prefs.getBoolean("haptics", true);
    deviceGravity = prefs.getBoolean("device_gravity", true);
    reducedMotion = prefs.getBoolean("reduced_motion", false);
    intensity = prefs.getString("animation_intensity", "NORMAL");
    today = LocalDate.now().toString();
    if (today.equals(prefs.getString("tapDate", ""))) todayTaps = prefs.getLong("todayTaps", 0);
    else prefs.edit().putString("tapDate", today).putLong("todayTaps", 0).apply();
  }

  void recordTap(int streak) {
    String currentDate = LocalDate.now().toString();
    if (!today.equals(currentDate)) {
      today = currentDate;
      todayTaps = 0;
    }
    totalTaps++;
    todayTaps++;
    pendingTaps++;
    bestStreak = Math.max(bestStreak, streak);
    if (pendingTaps >= 20) flush();
  }

  void flush() {
    if (pendingTaps == 0) return;
    pendingTaps = 0;
    prefs
        .edit()
        .putLong("totalTaps", totalTaps)
        .putLong("todayTaps", todayTaps)
        .putInt("bestStreak", bestStreak)
        .putString("tapDate", today)
        .apply();
  }

  long totalTaps() {
    return totalTaps;
  }

  long todayTaps() {
    return todayTaps;
  }

  int bestStreak() {
    return bestStreak;
  }

  String mode() {
    return mode;
  }

  String skin() {
    return skin;
  }

  boolean sound() {
    return sound;
  }

  boolean haptics() {
    return haptics;
  }

  boolean deviceGravity() {
    return deviceGravity;
  }

  boolean reducedMotion() {
    return reducedMotion;
  }

  String intensity() {
    return intensity;
  }

  int bestSpeed() {
    return prefs.getInt("best_speed", 0);
  }

  void setMode(String value) {
    mode = value;
    prefs.edit().putString("mode", value).apply();
  }

  void setSkin(String value) {
    skin = value;
    prefs.edit().putString("skin", value).apply();
  }

  void setSound(boolean value) {
    sound = value;
    prefs.edit().putBoolean("sound", value).apply();
  }

  void setHaptics(boolean value) {
    haptics = value;
    prefs.edit().putBoolean("haptics", value).apply();
  }

  void setDeviceGravity(boolean value) {
    deviceGravity = value;
    prefs.edit().putBoolean("device_gravity", value).apply();
  }

  void setReducedMotion(boolean value) {
    reducedMotion = value;
    prefs.edit().putBoolean("reduced_motion", value).apply();
  }

  void setIntensity(String value) {
    intensity = value;
    prefs.edit().putString("animation_intensity", value).apply();
  }

  boolean achievement(String id) {
    return prefs.getBoolean("achievement_" + id, false);
  }

  void unlock(String id) {
    prefs.edit().putBoolean("achievement_" + id, true).apply();
  }

  void saveRecord(GameRecord record) {
    List<GameRecord> records = records();
    records.add(0, record);
    if (records.size() > MAX_RECORDS) records = records.subList(0, MAX_RECORDS);
    Set<String> encoded = new HashSet<>();
    for (GameRecord item : records) encoded.add(item.encode());
    SharedPreferences.Editor editor = prefs.edit().putStringSet(RECORDS, encoded);
    if ("SPEED".equals(record.mode) && record.missions > bestSpeed())
      editor.putInt("best_speed", record.missions);
    editor.apply();
  }

  List<GameRecord> records() {
    Set<String> values = prefs.getStringSet(RECORDS, Collections.emptySet());
    List<GameRecord> result = new ArrayList<>();
    for (String value : values) {
      GameRecord record = GameRecord.decode(value);
      if (record != null) result.add(record);
    }
    result.sort(Comparator.comparingLong((GameRecord r) -> r.timestamp).reversed());
    return result;
  }

  void resetStatistics() {
    totalTaps = todayTaps = 0;
    bestStreak = pendingTaps = 0;
    prefs
        .edit()
        .remove("totalTaps")
        .remove("todayTaps")
        .remove("bestStreak")
        .remove(RECORDS)
        .remove("best_speed")
        .putString("tapDate", today)
        .apply();
  }

  void resetAchievements() {
    SharedPreferences.Editor editor = prefs.edit();
    for (long threshold : TAP_ACHIEVEMENTS) editor.remove("achievement_" + threshold);
    for (String id :
        new String[] {
          "speed_first", "speed_5", "speed_10", "speed_20", "perfect_alternation", "carry_master"
        }) editor.remove("achievement_" + id);
    editor.apply();
  }

  static String skinName(String id) {
    for (int i = 0; i < SKIN_IDS.length; i++) if (SKIN_IDS[i].equals(id)) return SKIN_NAMES[i];
    return SKIN_NAMES[0];
  }
}
