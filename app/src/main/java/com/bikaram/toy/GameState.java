package com.bikaram.toy;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

final class GameState {
    private final SharedPreferences prefs;
    long totalTaps;
    long todayTaps;
    int bestStreak;
    int duelWins;
    String mode;
    String skin;

    GameState(Context context) {
        prefs = context.getSharedPreferences("bikaram_state", Context.MODE_PRIVATE);
        totalTaps = prefs.getLong("totalTaps", 0);
        bestStreak = prefs.getInt("bestStreak", 0);
        duelWins = prefs.getInt("duelWins", 0);
        mode = prefs.getString("mode", "NORMAL");
        skin = prefs.getString("skin", "CLASSIC");
        String today = LocalDate.now().toString();
        if (!today.equals(prefs.getString("tapDate", ""))) {
            todayTaps = 0;
            prefs.edit().putString("tapDate", today).putLong("todayTaps", 0).apply();
        } else {
            todayTaps = prefs.getLong("todayTaps", 0);
        }
    }

    void onTap(int streak) {
        totalTaps++;
        todayTaps++;
        bestStreak = Math.max(bestStreak, streak);
        persist();
    }

    void setMode(String mode) {
        this.mode = mode;
        persist();
    }

    void setSkin(String skin) {
        this.skin = skin;
        persist();
    }

    void addDuelWin() {
        duelWins++;
        persist();
    }

    boolean achievementUnlocked(long threshold) {
        return prefs.getBoolean("achievement_" + threshold, false);
    }

    void unlockAchievement(long threshold) {
        prefs.edit().putBoolean("achievement_" + threshold, true).apply();
    }

    private void persist() {
        prefs.edit()
                .putLong("totalTaps", totalTaps)
                .putLong("todayTaps", todayTaps)
                .putInt("bestStreak", bestStreak)
                .putInt("duelWins", duelWins)
                .putString("mode", mode)
                .putString("skin", skin)
                .putString("tapDate", LocalDate.now().toString())
                .apply();
    }
}
