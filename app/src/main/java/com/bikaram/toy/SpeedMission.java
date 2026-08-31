package com.bikaram.toy;

final class SpeedMission {
  enum Type {
    LEFT,
    RIGHT,
    TOTAL,
    EACH,
    ALTERNATE,
    SEQUENCE,
    ASYMMETRIC,
    EXACT_LEFT,
    EXACT_RIGHT
  }

  final int id;
  final Type type;
  final String title;
  final float baseSeconds;
  final int leftTarget;
  final int rightTarget;
  final int totalTarget;
  final int difficulty;

  SpeedMission(
      int id,
      Type type,
      String title,
      float baseSeconds,
      int leftTarget,
      int rightTarget,
      int totalTarget,
      int difficulty) {
    this.id = id;
    this.type = type;
    this.title = title;
    this.baseSeconds = baseSeconds;
    this.leftTarget = leftTarget;
    this.rightTarget = rightTarget;
    this.totalTarget = totalTarget;
    this.difficulty = difficulty;
  }

  int targetForProgress() {
    if (type == Type.EACH || type == Type.ASYMMETRIC || type == Type.SEQUENCE)
      return Math.abs(leftTarget) + Math.abs(rightTarget);
    return totalTarget > 0 ? totalTarget : Math.max(leftTarget, rightTarget);
  }
}
