package com.functionize.health.classification;

import com.functionize.health.event.ExecutionEvent;
import com.functionize.health.event.Status;
import java.util.List;
import java.util.Locale;

public final class TestClassifier {
    public static final int WINDOW_SIZE = 20;
    static final int MINIMUM_EVIDENCE = 5;
    static final int BROKEN_STREAK = 5;
    static final double HEALTHY_RATE_MAX = 0.10;
    static final double BROKEN_RATE_MIN = 0.80;
    private static final double Z_95 = 1.96;

    public ClassificationResult classify(List<ExecutionEvent> newestFirst) {
        var window = newestFirst.stream()
                .filter(event -> event.status() != Status.SKIPPED)
                .limit(WINDOW_SIZE)
                .toList();

        var passed = count(window, Status.PASSED);
        var failed = count(window, Status.FAILED);
        var errored = count(window, Status.ERRORED);
        var nonPassing = failed + errored;
        var total = window.size();
        var nonPassRate = total == 0 ? 0.0 : (double) nonPassing / total;
        var streak = currentNonPassStreak(window);
        var interval = wilsonInterval(nonPassing, total);

        var classification = classification(total, nonPassRate, streak);
        var confidence = classification == Classification.INSUFFICIENT_DATA
                ? 0.0
                : ((double) total / WINDOW_SIZE) * (1.0 - (interval.upper() - interval.lower()));
        var failureMode = failureMode(failed, errored);
        var evidence = new ClassificationResult.Evidence(
                total,
                passed,
                failed,
                errored,
                round(nonPassRate),
                streak,
                new ClassificationResult.WilsonInterval(round(interval.lower()), round(interval.upper())));

        return new ClassificationResult(
                classification,
                failureMode,
                round(clamp(confidence)),
                reasoning(classification, evidence),
                evidence);
    }

    private static Classification classification(int total, double nonPassRate, int streak) {
        if (total < MINIMUM_EVIDENCE) {
            return Classification.INSUFFICIENT_DATA;
        }
        if (streak >= BROKEN_STREAK || nonPassRate >= BROKEN_RATE_MIN) {
            return Classification.BROKEN;
        }
        if (nonPassRate <= HEALTHY_RATE_MAX) {
            return Classification.HEALTHY;
        }
        return Classification.FLAKY;
    }

    private static String reasoning(Classification classification, ClassificationResult.Evidence evidence) {
        if (classification == Classification.INSUFFICIENT_DATA) {
            var runLabel = evidence.windowSize() == 1 ? "run" : "runs";
            return String.format(
                    Locale.ROOT,
                    "%d decisive %s available; at least %d are required.",
                    evidence.windowSize(), runLabel, MINIMUM_EVIDENCE);
        }
        if (classification == Classification.BROKEN && evidence.consecutiveNonPasses() >= BROKEN_STREAK) {
            return String.format(
                    Locale.ROOT,
                    "The latest %d decisive runs were non-passing; %d of %d in the window were non-passing.",
                    evidence.consecutiveNonPasses(),
                    evidence.failed() + evidence.errored(),
                    evidence.windowSize());
        }
        return String.format(
                Locale.ROOT,
                "Non-passing outcomes: %d of %d decisive runs (%d failed, %d errored).",
                evidence.failed() + evidence.errored(),
                evidence.windowSize(),
                evidence.failed(),
                evidence.errored());
    }

    private static FailureMode failureMode(int failed, int errored) {
        if (failed == 0 && errored == 0) {
            return FailureMode.NONE;
        }
        if (failed > 0 && errored == 0) {
            return FailureMode.ASSERTION;
        }
        if (failed == 0) {
            return FailureMode.EXECUTION;
        }
        return FailureMode.MIXED;
    }

    private static int currentNonPassStreak(List<ExecutionEvent> newestFirst) {
        var streak = 0;
        for (var event : newestFirst) {
            if (event.status() == Status.PASSED) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private static int count(List<ExecutionEvent> events, Status status) {
        return (int) events.stream().filter(event -> event.status() == status).count();
    }

    // Wilson score avoids the misleading certainty of a raw failure rate for small samples.
    private static ClassificationResult.WilsonInterval wilsonInterval(int successes, int total) {
        if (total == 0) {
            return new ClassificationResult.WilsonInterval(0.0, 1.0);
        }
        var proportion = (double) successes / total;
        var zSquared = Z_95 * Z_95;
        var denominator = 1.0 + zSquared / total;
        var centre = (proportion + zSquared / (2.0 * total)) / denominator;
        var margin = Z_95
                * Math.sqrt((proportion * (1.0 - proportion) / total) + zSquared / (4.0 * total * total))
                / denominator;
        return new ClassificationResult.WilsonInterval(clamp(centre - margin), clamp(centre + margin));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }
}
