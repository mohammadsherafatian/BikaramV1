package com.bikaram.toy;

final class PhysicsBall {
  float anchorX;
  float anchorY;
  float ropeLength;
  float radius;
  float angle;
  float angularVelocity;
  float x;
  float y;
  private float softLimit = 1.08f;

  PhysicsBall(float angle) {
    this.angle = angle;
  }

  void layout(float ax, float ay, float length, float radius) {
    this.anchorX = ax;
    this.anchorY = ay;
    this.ropeLength = length;
    this.radius = radius;
    updatePosition();
  }

  void setSoftLimit(float softLimit) {
    this.softLimit = softLimit;
  }

  void step(float dt, float gravity, float damping, float horizontalGravity) {
    float angularAcceleration = -(gravity / Math.max(ropeLength, 1f)) * (float) Math.sin(angle);
    angularAcceleration += (horizontalGravity / Math.max(ropeLength, 1f)) * (float) Math.cos(angle);
    // A progressive spring near the edge keeps aggressive taps funny without hard clipping.
    float overshoot = Math.abs(angle) - softLimit;
    if (overshoot > 0f) angularAcceleration -= Math.signum(angle) * overshoot * 22f;
    angularVelocity += angularAcceleration * dt;
    angularVelocity *= (float) Math.pow(damping, dt * 60f);
    angularVelocity = clamp(angularVelocity, -8f, 8f);
    angle += angularVelocity * dt;
    if (angle < -1.40f || angle > 1.40f) {
      angle = clamp(angle, -1.40f, 1.40f);
      angularVelocity *= -.24f;
    }
    updatePosition();
  }

  void impulse(float amount) {
    angularVelocity += amount;
  }

  void updatePosition() {
    x = anchorX + ropeLength * (float) Math.sin(angle);
    y = anchorY + ropeLength * (float) Math.cos(angle);
  }

  boolean isResting() {
    return Math.abs(angle) < .006f && Math.abs(angularVelocity) < .012f;
  }

  void settle() {
    angle = 0f;
    angularVelocity = 0f;
    updatePosition();
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }
}
