package com.bikaram.toy;

final class SpeedMissionEngine {
  enum TapResult {
    CONTINUE,
    COMPLETE,
    FAILED
  }

  private SpeedMission mission;
  private int left;
  private int right;
  private int lastSide = -1;
  private boolean failed;

  void start(SpeedMission mission) {
    this.mission = mission;
    left = right = 0;
    lastSide = -1;
    failed = false;
  }

  TapResult tap(int side) {
    if (mission == null || failed) return TapResult.FAILED;
    if (mission.type == SpeedMission.Type.ALTERNATE && lastSide == side) failed = true;
    if (mission.type == SpeedMission.Type.EXACT_LEFT && side != 0) failed = true;
    if (mission.type == SpeedMission.Type.EXACT_RIGHT && side != 1) failed = true;
    if (mission.type == SpeedMission.Type.SEQUENCE) {
      boolean rightFirst = mission.leftTarget < 0;
      int firstCount = rightFirst ? right : left;
      int firstTarget = Math.abs(rightFirst ? mission.rightTarget : mission.leftTarget);
      if (firstCount < firstTarget && side != (rightFirst ? 1 : 0)) failed = true;
    }
    if (failed) return TapResult.FAILED;
    if (side == 0) left++;
    else right++;
    lastSide = side;
    return isComplete() ? TapResult.COMPLETE : TapResult.CONTINUE;
  }

  boolean isComplete() {
    if (mission == null) return false;
    switch (mission.type) {
      case LEFT:
      case EXACT_LEFT:
        return left >= mission.leftTarget;
      case RIGHT:
      case EXACT_RIGHT:
        return right >= mission.rightTarget;
      case TOTAL:
      case ALTERNATE:
        return left + right >= mission.totalTarget;
      case EACH:
      case ASYMMETRIC:
        return left >= mission.leftTarget && right >= mission.rightTarget;
      case SEQUENCE:
        return left >= Math.abs(mission.leftTarget) && right >= Math.abs(mission.rightTarget);
      default:
        return false;
    }
  }

  int progress() {
    if (mission == null) return 0;
    if (mission.type == SpeedMission.Type.LEFT || mission.type == SpeedMission.Type.EXACT_LEFT)
      return left;
    if (mission.type == SpeedMission.Type.RIGHT || mission.type == SpeedMission.Type.EXACT_RIGHT)
      return right;
    return left + right;
  }

  int left() {
    return left;
  }

  int right() {
    return right;
  }
}
