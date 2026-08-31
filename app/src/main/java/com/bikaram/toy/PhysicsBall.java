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

    void step(float dt, float gravity, float damping, float horizontalGravity) {
        float angularAcceleration = -(gravity / Math.max(ropeLength, 1f)) * (float) Math.sin(angle);
        angularAcceleration += (horizontalGravity / Math.max(ropeLength, 1f)) * (float) Math.cos(angle);
        angularVelocity += angularAcceleration * dt;
        angularVelocity *= (float) Math.pow(damping, dt * 60f);
        angle += angularVelocity * dt;
        angle = Math.max(-1.45f, Math.min(1.45f, angle));
        updatePosition();
    }

    void impulse(float amount) {
        angularVelocity += amount;
    }

    void updatePosition() {
        x = anchorX + ropeLength * (float) Math.sin(angle);
        y = anchorY + ropeLength * (float) Math.cos(angle);
    }
}
