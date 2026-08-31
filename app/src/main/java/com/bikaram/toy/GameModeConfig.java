package com.bikaram.toy;

import java.util.Locale;

final class GameModeConfig {
  static final String[] IDS = {"NORMAL", "ZEN", "RAGE", "OFFICE", "TURBO", "GRAVITY"};
  static final String[] NAMES = {"Normal", "Zen", "Rage", "Office", "Turbo", "Gravity"};

  final String id;
  final float gravity;
  final float damping;
  final float tapImpulse;
  final float collisionStrength;
  final float motionLimit;
  final float sensorStrength;
  final boolean silent;

  private GameModeConfig(
      String id,
      float gravity,
      float damping,
      float tapImpulse,
      float collisionStrength,
      float motionLimit,
      float sensorStrength,
      boolean silent) {
    this.id = id;
    this.gravity = gravity;
    this.damping = damping;
    this.tapImpulse = tapImpulse;
    this.collisionStrength = collisionStrength;
    this.motionLimit = motionLimit;
    this.sensorStrength = sensorStrength;
    this.silent = silent;
  }

  static GameModeConfig forId(String value) {
    String id = value == null ? "NORMAL" : value.toUpperCase(Locale.US);
    switch (id) {
      case "ZEN":
        return new GameModeConfig(id, 570f, .970f, 1.00f, .52f, 1.02f, 12f, false);
      case "RAGE":
        return new GameModeConfig(id, 850f, .989f, 2.15f, .92f, 1.20f, 22f, false);
      case "OFFICE":
        return new GameModeConfig(id, 700f, .978f, 1.12f, .55f, .98f, 10f, true);
      case "TURBO":
        return new GameModeConfig(id, 940f, .992f, 2.60f, 1.02f, 1.25f, 24f, false);
      case "GRAVITY":
        return new GameModeConfig(id, 690f, .984f, 1.48f, .70f, 1.12f, 125f, false);
      default:
        return new GameModeConfig("NORMAL", 760f, .982f, 1.38f, .68f, 1.08f, 16f, false);
    }
  }

  static String displayName(String id) {
    for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
    return NAMES[0];
  }
}
