package com.bikaram.toy;

import java.time.LocalDate;

final class DailyChallenge {
    enum Type { SPEED, LEFT_ONLY, ALTERNATE }

    final Type type;
    final String title;
    final String description;
    final int target;
    final long durationMs;

    private DailyChallenge(Type type, String title, String description, int target, long durationMs) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.target = target;
        this.durationMs = durationMs;
    }

    static DailyChallenge today() {
        long n = LocalDate.now().toEpochDay();
        int index = (int) Math.floorMod(n, 3);
        if (index == 0) {
            return new DailyChallenge(Type.SPEED, "چالش سرعت", "در ۵ ثانیه ۳۷ ضربه بزن", 37, 5000);
        } else if (index == 1) {
            return new DailyChallenge(Type.LEFT_ONLY, "چپ رو ول نکن", "۵۰ بار فقط سمت چپ؛ راست ممنوع", 50, 0);
        }
        return new DailyChallenge(Type.ALTERNATE, "یکی‌درمیون", "۴۰ ضربه کاملاً یکی‌درمیون", 40, 0);
    }
}
