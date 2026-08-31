package com.bikaram.toy;

import static org.junit.Assert.*;

import org.junit.Test;

public class PhysicsBallTest {
  @Test
  public void deltaTimeProducesComparableMotion() {
    PhysicsBall sixty = ball();
    PhysicsBall oneTwenty = ball();
    sixty.impulse(2f);
    oneTwenty.impulse(2f);
    for (int i = 0; i < 60; i++) sixty.step(1f / 60f, 760, .982f, 0);
    for (int i = 0; i < 120; i++) oneTwenty.step(1f / 120f, 760, .982f, 0);
    assertEquals(sixty.angle, oneTwenty.angle, .035f);
  }

  @Test
  public void softConstraintKeepsBodyOnPlausibleArc() {
    PhysicsBall ball = ball();
    ball.impulse(100f);
    for (int i = 0; i < 240; i++) ball.step(1f / 60f, 760, .982f, 0);
    assertTrue(Math.abs(ball.angle) <= 1.401f);
    assertTrue(Math.abs(ball.angularVelocity) <= 8f);
  }

  private PhysicsBall ball() {
    PhysicsBall ball = new PhysicsBall(0);
    ball.layout(100, 100, 300, 60);
    return ball;
  }
}
