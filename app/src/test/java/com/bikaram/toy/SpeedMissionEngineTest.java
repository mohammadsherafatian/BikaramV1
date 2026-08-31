package com.bikaram.toy;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;

public class SpeedMissionEngineTest {
  @Test
  public void catalogHasTwentyDataDrivenMissions() {
    assertEquals(20, SpeedMissionCatalog.templates().size());
    for (SpeedMission mission : SpeedMissionCatalog.templates()) {
      assertTrue(mission.baseSeconds >= 4f);
      assertTrue(mission.targetForProgress() > 0);
    }
  }

  @Test
  public void alternatingMissionRejectsRepeatedSide() {
    SpeedMissionEngine engine = new SpeedMissionEngine();
    engine.start(new SpeedMission(1, SpeedMission.Type.ALTERNATE, "alternate", 5, 0, 0, 4, 1));
    assertEquals(SpeedMissionEngine.TapResult.CONTINUE, engine.tap(0));
    assertEquals(SpeedMissionEngine.TapResult.FAILED, engine.tap(0));
  }

  @Test
  public void eachSideRequiresBothTargets() {
    SpeedMissionEngine engine = new SpeedMissionEngine();
    engine.start(new SpeedMission(1, SpeedMission.Type.EACH, "each", 5, 2, 2, 0, 1));
    engine.tap(0);
    engine.tap(0);
    engine.tap(1);
    assertFalse(engine.isComplete());
    assertEquals(SpeedMissionEngine.TapResult.COMPLETE, engine.tap(1));
  }

  @Test
  public void generatedMissionsScaleButRemainPlausible() {
    Random random = new Random(7);
    for (int index = 20; index < 200; index++) {
      SpeedMission mission = SpeedMissionCatalog.forIndex(index, random);
      assertTrue(mission.targetForProgress() > 0);
      assertTrue(mission.baseSeconds >= mission.targetForProgress() / 4.6f);
    }
  }
}
