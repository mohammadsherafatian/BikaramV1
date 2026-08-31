package com.bikaram.toy;

import java.util.Locale;

final class GameRecord {
  final String mode;
  final int score;
  final int missions;
  final long taps;
  final long durationMs;
  final String skinId;
  final long timestamp;
  final int highestDifficulty;
  final float maxCarrySeconds;

  GameRecord(
      String mode,
      int score,
      int missions,
      long taps,
      long durationMs,
      String skinId,
      long timestamp,
      int highestDifficulty,
      float maxCarrySeconds) {
    this.mode = mode;
    this.score = score;
    this.missions = missions;
    this.taps = taps;
    this.durationMs = durationMs;
    this.skinId = skinId;
    this.timestamp = timestamp;
    this.highestDifficulty = highestDifficulty;
    this.maxCarrySeconds = maxCarrySeconds;
  }

  String encode() {
    return String.format(
        Locale.US,
        "%s|%d|%d|%d|%d|%s|%d|%d|%.2f",
        mode,
        score,
        missions,
        taps,
        durationMs,
        skinId,
        timestamp,
        highestDifficulty,
        maxCarrySeconds);
  }

  static GameRecord decode(String value) {
    try {
      String[] p = value.split("\\|", -1);
      if (p.length != 9) return null;
      return new GameRecord(
          p[0],
          Integer.parseInt(p[1]),
          Integer.parseInt(p[2]),
          Long.parseLong(p[3]),
          Long.parseLong(p[4]),
          p[5],
          Long.parseLong(p[6]),
          Integer.parseInt(p[7]),
          Float.parseFloat(p[8]));
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
