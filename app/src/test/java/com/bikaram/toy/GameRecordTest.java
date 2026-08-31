package com.bikaram.toy;

import static org.junit.Assert.*;

import org.junit.Test;

public class GameRecordTest {
  @Test
  public void recordRoundTripsAllSpeedMetadata() {
    GameRecord input =
        new GameRecord("SPEED", 1842, 18, 312, 198000, "WATERMELON", 1788165000000L, 7, 9.4f);
    GameRecord output = GameRecord.decode(input.encode());
    assertNotNull(output);
    assertEquals(input.mode, output.mode);
    assertEquals(18, output.missions);
    assertEquals(312, output.taps);
    assertEquals("WATERMELON", output.skinId);
    assertEquals(7, output.highestDifficulty);
    assertEquals(9.4f, output.maxCarrySeconds, .01f);
  }

  @Test
  public void malformedRecordIsIgnored() {
    assertNull(GameRecord.decode("bad|record"));
  }
}
