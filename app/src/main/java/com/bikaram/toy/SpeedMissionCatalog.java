package com.bikaram.toy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class SpeedMissionCatalog {
  private static final List<SpeedMission> TEMPLATES;

  static {
    List<SpeedMission> m = new ArrayList<>();
    m.add(x(1, SpeedMission.Type.LEFT, "Tap LEFT 8 times", 5, 8, 0, 0, 1));
    m.add(x(2, SpeedMission.Type.RIGHT, "Tap RIGHT 8 times", 5, 0, 8, 0, 1));
    m.add(x(3, SpeedMission.Type.TOTAL, "Tap either side 10 times", 8, 0, 0, 10, 1));
    m.add(x(4, SpeedMission.Type.LEFT, "Tap LEFT 15 times", 10, 15, 0, 0, 2));
    m.add(x(5, SpeedMission.Type.RIGHT, "Tap RIGHT 15 times", 10, 0, 15, 0, 2));
    m.add(x(6, SpeedMission.Type.EACH, "Tap EACH side 5 times", 10, 5, 5, 0, 2));
    m.add(x(7, SpeedMission.Type.TOTAL, "Land 30 total taps", 15, 0, 0, 30, 2));
    m.add(x(8, SpeedMission.Type.EXACT_LEFT, "Exactly 10 LEFT — no RIGHT", 8, 10, 0, 0, 3));
    m.add(x(9, SpeedMission.Type.EXACT_RIGHT, "Exactly 10 RIGHT — no LEFT", 8, 0, 10, 0, 3));
    m.add(x(10, SpeedMission.Type.ALTERNATE, "Alternate sides for 12 taps", 10, 0, 0, 12, 3));
    m.add(x(11, SpeedMission.Type.ALTERNATE, "Alternate sides for 20 taps", 14, 0, 0, 20, 4));
    m.add(x(12, SpeedMission.Type.SEQUENCE, "5 LEFT, then 5 RIGHT", 10, 5, 5, 0, 3));
    m.add(x(13, SpeedMission.Type.SEQUENCE, "5 RIGHT, then 5 LEFT", 10, -5, -5, 0, 3));
    m.add(x(14, SpeedMission.Type.TOTAL, "Reach 25 total taps", 10, 0, 0, 25, 3));
    m.add(x(15, SpeedMission.Type.TOTAL, "Reach 50 total taps", 25, 0, 0, 50, 4));
    m.add(x(16, SpeedMission.Type.ASYMMETRIC, "20 LEFT and 10 RIGHT", 20, 20, 10, 0, 4));
    m.add(x(17, SpeedMission.Type.ASYMMETRIC, "10 LEFT and 20 RIGHT", 20, 10, 20, 0, 4));
    m.add(x(18, SpeedMission.Type.ALTERNATE, "Alternate for 30 taps", 20, 0, 0, 30, 5));
    m.add(x(19, SpeedMission.Type.EACH, "Tap EACH side 15 times", 20, 15, 15, 0, 5));
    m.add(x(20, SpeedMission.Type.TOTAL, "Reach 60 total taps", 30, 0, 0, 60, 5));
    TEMPLATES = Collections.unmodifiableList(m);
  }

  private static SpeedMission x(
      int id,
      SpeedMission.Type type,
      String title,
      float seconds,
      int left,
      int right,
      int total,
      int difficulty) {
    return new SpeedMission(id, type, title, seconds, left, right, total, difficulty);
  }

  static List<SpeedMission> templates() {
    return TEMPLATES;
  }

  static SpeedMission forIndex(int missionIndex, Random random) {
    if (missionIndex < TEMPLATES.size()) return TEMPLATES.get(missionIndex);
    int level = 1 + missionIndex / 5;
    SpeedMission base = TEMPLATES.get(random.nextInt(TEMPLATES.size()));
    int boost = Math.min(30, level * 2);
    float timeBoost = boost / 4f;
    int left = base.leftTarget == 0 ? 0 : signedGrow(base.leftTarget, boost / 2);
    int right = base.rightTarget == 0 ? 0 : signedGrow(base.rightTarget, boost / 2);
    int total = base.totalTarget == 0 ? 0 : base.totalTarget + boost;
    String title = describe(base.type, left, right, total);
    float minimum = Math.max(4f, (Math.abs(left) + Math.abs(right) + total) / 4.5f);
    return new SpeedMission(
        missionIndex + 1,
        base.type,
        title,
        Math.max(minimum, base.baseSeconds + timeBoost),
        left,
        right,
        total,
        Math.min(10, base.difficulty + level / 2));
  }

  private static int signedGrow(int value, int boost) {
    return value < 0 ? value - boost : value + boost;
  }

  private static String describe(SpeedMission.Type type, int left, int right, int total) {
    switch (type) {
      case LEFT:
        return "Tap LEFT " + left + " times";
      case RIGHT:
        return "Tap RIGHT " + right + " times";
      case TOTAL:
        return "Land " + total + " total taps";
      case EACH:
        return "Tap EACH side " + left + " times";
      case ALTERNATE:
        return "Alternate for " + total + " taps";
      case EXACT_LEFT:
        return "Exactly " + left + " LEFT — no RIGHT";
      case EXACT_RIGHT:
        return "Exactly " + right + " RIGHT — no LEFT";
      case SEQUENCE:
        return left < 0
            ? Math.abs(right) + " RIGHT, then " + Math.abs(left) + " LEFT"
            : left + " LEFT, then " + right + " RIGHT";
      default:
        return left + " LEFT and " + right + " RIGHT";
    }
  }
}
