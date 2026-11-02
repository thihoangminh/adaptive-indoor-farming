package com.farm.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
// import java.util.Objects;

public class LightingController {

    public record Decision(
            double hour,
            double naturalPpfd,
            double accumulatedDli,
            double remainingDli,
            double forecastNaturalDli,
            double requestedLedPpfd,
            double ledFraction,
            boolean targetReachable,
            String reason) {}

    private final double targetDli;
    private final double ledMaxPpfd;
    private final double windowEndHour;
    private final double minPpfdToSwitchOn;

    private double accumulatedDli = 0.0;
    private final List<Decision> log = new ArrayList<>();

    public LightingController(
            double targetDli,
            double ledMaxPpfd,
            double windowEndHour,
            double minPpfdToSwitchOn) {

        if (!Double.isFinite(targetDli) || targetDli < 0)
            throw new IllegalArgumentException("Target DLI must be nonnegative.");

        if (!Double.isFinite(ledMaxPpfd) || ledMaxPpfd <= 0)
            throw new IllegalArgumentException("Maximum LED PPFD must be positive.");

        if (!Double.isFinite(windowEndHour)
                || windowEndHour <= 0 || windowEndHour > 24)
            throw new IllegalArgumentException("Window end must be in (0, 24].");

        if (!Double.isFinite(minPpfdToSwitchOn)
                || minPpfdToSwitchOn < 0)
            throw new IllegalArgumentException("Minimum PPFD must be nonnegative.");

        this.targetDli = targetDli;
        this.ledMaxPpfd = ledMaxPpfd;
        this.windowEndHour = windowEndHour;
        this.minPpfdToSwitchOn = minPpfdToSwitchOn;
    }

    public LightingController(double targetDli, double ledMaxPpfd) {
        this(targetDli, ledMaxPpfd, 22.0, 5.0);
    }

    /**
     * Forecast must cover natural light from this time through
     * the lighting-window end, including the current step.
     */
    public Decision decide(
            double naturalPpfd,
            double hour,
            double forecastNaturalDli) {

        requireNonnegativeFinite(naturalPpfd, "Natural PPFD");
        requireNonnegativeFinite(forecastNaturalDli, "Forecast natural DLI");

        if (!Double.isFinite(hour) || hour < 0 || hour > 24)
            throw new IllegalArgumentException("Hour must be between 0 and 24.");

        double remaining = Math.max(0.0, targetDli - accumulatedDli);
        double secondsLeft = (windowEndHour - hour) * 3600.0;

        double requested = 0.0;
        double fraction = 0.0;
        boolean reachable = true;
        String reason;

        if (remaining <= 0.0) {
            reason = "Target already reached; LED off.";

        } else if (secondsLeft <= 0.0) {
            reachable = false;
            reason = "Lighting window ended before the target was reached.";

        } else {
            double deficit = remaining - forecastNaturalDli;

            if (deficit <= 0.0) {
                reason = "Forecast sunlight is sufficient; LED off.";

            } else {
                requested = deficit * 1.0e6 / secondsLeft;

                if (requested > ledMaxPpfd) {
                    fraction = 1.0;
                    reachable = false;
                    reason = "LED at maximum. Required average PPFD is "
                            + round(requested) + ", above the limit of "
                            + round(ledMaxPpfd)
                            + "; target is physically unreachable under this forecast.";

                } else if (requested < minPpfdToSwitchOn) {
                    requested = 0.0;
                    reason = "Small deficit below the switch-on threshold; "
                            + "LED remains off. A slight target miss is possible.";

                } else {
                    fraction = requested / ledMaxPpfd;
                    reason = "Estimated deficit: " + round(deficit)
                            + " mol/m2 over " + round(secondsLeft / 3600.0)
                            + " hours. LED set to " + round(fraction * 100) + "%.";
                }
            }
        }

        Decision decision = new Decision(
                hour, naturalPpfd, accumulatedDli, remaining,
                forecastNaturalDli, requested, fraction, reachable, reason);

        log.add(decision);
        return decision;
    }

    /** Call once per simulated step, after applying the LED command. */
    public void commitStep(
            double naturalPpfd,
            double ledPpfd,
            double stepSeconds) {

        requireNonnegativeFinite(naturalPpfd, "Natural PPFD");
        requireNonnegativeFinite(ledPpfd, "LED PPFD");

        if (!Double.isFinite(stepSeconds) || stepSeconds <= 0)
            throw new IllegalArgumentException("Step duration must be positive.");

        accumulatedDli +=
                (naturalPpfd + ledPpfd) * stepSeconds / 1.0e6;
    }

    public double getAccumulatedDli() {
        return accumulatedDli;
    }

    public double getTargetDli() {
        return targetDli;
    }

    public List<Decision> getLog() {
        return List.copyOf(log);
    }

    public void reset() {
        accumulatedDli = 0.0;
        log.clear();
    }

    private static void requireNonnegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException(
                    name + " must be finite and nonnegative.");
    }

    private static String round(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
