package com.farm.simulation;

// import java.util.Objects;

public class CostAnalyser {

    public static class Ledger {
        private double energyKWh = 0.0;

        public void addInterval(double powerWatts, double seconds) {
            requireNonnegativeFinite(powerWatts, "Power");
            requireNonnegativeFinite(seconds, "Duration");

            energyKWh += powerWatts * seconds / 3_600_000.0;

            if (!Double.isFinite(energyKWh)) {
                throw new ArithmeticException("Energy total overflowed.");
            }
        }

        public double getEnergyKWh() {
            return energyKWh;
        }

        public void reset() {
            energyKWh = 0.0;
        }
    }

    public record Comparison(
            double adaptiveKWh,
            double baselineKWh,
            double kWhSaved,
            double kWhSavedPercent,
            double adaptiveCost,
            double baselineCost,
            double costSaved,
            double costSavedPercent) {}

    private final double pricePerKWh;
    private final String currency;

    private final Ledger adaptive = new Ledger();
    private final Ledger baseline = new Ledger();

    public CostAnalyser(double pricePerKWh, String currency) {
        requireNonnegativeFinite(pricePerKWh, "Electricity price");

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                    "Currency label must not be blank.");
        }

        this.pricePerKWh = pricePerKWh;
        this.currency = currency.trim();
    }

    public void recordAdaptive(double powerWatts, double seconds) {
        adaptive.addInterval(powerWatts, seconds);
    }

    public void recordBaseline(double powerWatts, double seconds) {
        baseline.addInterval(powerWatts, seconds);
    }

    public double costOf(double energyKWh) {
        requireNonnegativeFinite(energyKWh, "Energy");
        return energyKWh * pricePerKWh;
    }

    public double getAdaptiveKWh() {
        return adaptive.getEnergyKWh();
    }

    public double getBaselineKWh() {
        return baseline.getEnergyKWh();
    }

    public String getCurrency() {
        return currency;
    }

    public Comparison compare() {
        double adaptiveEnergy = adaptive.getEnergyKWh();
        double baselineEnergy = baseline.getEnergyKWh();

        double adaptiveCost = costOf(adaptiveEnergy);
        double baselineCost = costOf(baselineEnergy);

        double energySaved = baselineEnergy - adaptiveEnergy;
        double costSaved = baselineCost - adaptiveCost;

        // Percentage savings are defined relative to the baseline.
        // If the baseline is zero, report 0% by display convention.
        double energyPercent = baselineEnergy > 0
                ? energySaved / baselineEnergy * 100.0
                : 0.0;

        double costPercent = baselineCost > 0
                ? costSaved / baselineCost * 100.0
                : 0.0;

        return new Comparison(
                adaptiveEnergy,
                baselineEnergy,
                energySaved,
                energyPercent,
                adaptiveCost,
                baselineCost,
                costSaved,
                costPercent);
    }

    public void reset() {
        adaptive.reset();
        baseline.reset();
    }

    private static void requireNonnegativeFinite(
            double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(
                    name + " must be finite and nonnegative.");
        }
    }
}

