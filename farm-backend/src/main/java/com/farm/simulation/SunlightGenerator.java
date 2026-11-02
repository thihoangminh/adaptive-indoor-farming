package com.farm.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates synthetic natural sunlight for an indoor-farming simulation.
 *
 * This is a simplified mathematical model, not measured sunlight data.
 *
 * Spectral convention:
 * - Wavelength samples: 400, 420, ..., 700 nm (16 points).
 * - spectrum[i] is spectral photon flux density in
 *   micromol m^-2 s^-1 nm^-1.
 * - PPFD is the trapezoidal integral of the spectrum from 400 to 700 nm.
 *
 * Requires Java 16+.
 */
public class SunlightGenerator {

    public static final int WL_START = 400;
    public static final int WL_END = 700;
    public static final int WL_STEP = 20;
    public static final int BANDS = (WL_END - WL_START) / WL_STEP + 1;

    public static int wavelength(int i) {
        if (i < 0 || i >= BANDS) {
            throw new IndexOutOfBoundsException(
                    "Wavelength index must be between 0 and " + (BANDS - 1)
            );
        }
        return WL_START + i * WL_STEP;
    }

    public enum WeatherPreset {
        SUNNY(1.00, 0.10, 0.00),
        CLOUDY(0.35, 0.60, 0.15),
        RAINY(0.12, 0.90, 0.10);

        public final double transmission;
        public final double diffuse;
        public final double flicker;

        WeatherPreset(double transmission, double diffuse, double flicker) {
            this.transmission = transmission;
            this.diffuse = diffuse;
            this.flicker = flicker;
        }
    }

    /**
     * One sunlight sample.
     * The spectrum is defensively copied to protect internal data.
     */
    public record SunlightSample(
            double hour,
            double[] spectrum,
            double ppfd
    ) {
        public SunlightSample {
            if (!Double.isFinite(hour) || hour < 0.0 || hour > 24.0) {
                throw new IllegalArgumentException("Hour must be in [0, 24].");
            }
            if (spectrum == null || spectrum.length != BANDS) {
                throw new IllegalArgumentException(
                        "Spectrum must contain exactly " + BANDS + " values."
                );
            }
            if (!Double.isFinite(ppfd) || ppfd < 0.0) {
                throw new IllegalArgumentException(
                        "PPFD must be finite and non-negative."
                );
            }

            spectrum = spectrum.clone();

            for (double value : spectrum) {
                if (!Double.isFinite(value) || value < 0.0) {
                    throw new IllegalArgumentException(
                            "Spectrum values must be finite and non-negative."
                    );
                }
            }
        }

        @Override
        public double[] spectrum() {
            return spectrum.clone();
        }
    }

    private final double sunriseHour;
    private final double sunsetHour;
    private final double peakPpfdOutside;
    private final double canopyTransmission;

    public SunlightGenerator(
            double sunriseHour,
            double sunsetHour,
            double peakPpfdOutside,
            double canopyTransmission
    ) {
        if (!Double.isFinite(sunriseHour)
                || !Double.isFinite(sunsetHour)
                || sunriseHour < 0.0
                || sunsetHour > 24.0
                || sunsetHour <= sunriseHour) {
            throw new IllegalArgumentException(
                    "Require 0 <= sunrise < sunset <= 24."
            );
        }

        if (!Double.isFinite(peakPpfdOutside)
                || peakPpfdOutside < 0.0) {
            throw new IllegalArgumentException(
                    "Peak PPFD must be finite and non-negative."
            );
        }

        if (!Double.isFinite(canopyTransmission)
                || canopyTransmission < 0.0
                || canopyTransmission > 1.0) {
            throw new IllegalArgumentException(
                    "Canopy transmission must be between 0 and 1."
            );
        }

        this.sunriseHour = sunriseHour;
        this.sunsetHour = sunsetHour;
        this.peakPpfdOutside = peakPpfdOutside;
        this.canopyTransmission = canopyTransmission;
    }

    public SunlightGenerator() {
        this(6.0, 20.0, 1000.0, 0.70);
    }

    /**
     * Generates sunlight at a specified hour (decimal hours, 0-24).
     */
    public SunlightSample generateSample(
            WeatherPreset weather,
            double hour
    ) {
        Objects.requireNonNull(weather, "Weather preset cannot be null.");

        if (!Double.isFinite(hour) || hour < 0.0 || hour > 24.0) {
            throw new IllegalArgumentException("Hour must be in [0, 24].");
        }

        double[] spectrum = new double[BANDS];

        // Simplified daylight curve: zero at sunrise/sunset, peak at noon.
        double elevation = 0.0;

        if (hour > sunriseHour && hour < sunsetHour) {
            elevation = Math.sin(
                    Math.PI * (hour - sunriseHour)
                            / (sunsetHour - sunriseHour)
            );
        }

        if (elevation <= 0.0) {
            return new SunlightSample(hour, spectrum, 0.0);
        }

        // Deterministic cloud variation for reproducible comparisons.
        double flicker = 1.0
                + weather.flicker
                * Math.sin(hour * 7.3)
                * Math.sin(hour * 2.1);

        double ppfd = peakPpfdOutside
                * canopyTransmission
                * weather.transmission
                * elevation
                * flicker;

        // Simplified spectral tilt, not a measured solar spectrum.
        double tilt = -0.25 * weather.diffuse
                + 0.50 * (1.0 - elevation);

        double[] weights = new double[BANDS];

        for (int i = 0; i < BANDS; i++) {
            double relativeWavelength =
                    (wavelength(i) - 550.0) / 150.0;

            weights[i] = Math.max(
                    0.05,
                    1.0 + tilt * relativeWavelength
            );
        }

        // Normalize using the same integration convention as PPFD.
        double weightIntegral = integrateSpectrum(weights);

        for (int i = 0; i < BANDS; i++) {
            spectrum[i] = ppfd * weights[i] / weightIntegral;
        }

        return new SunlightSample(
                hour,
                spectrum,
                integrateSpectrum(spectrum)
        );
    }

    /**
     * Integrates spectral photon flux density over 400-700 nm.
     * Uses the trapezoidal rule with 20 nm spacing.
     */
    public static double integrateSpectrum(double[] spectrum) {
        if (spectrum == null || spectrum.length != BANDS) {
            throw new IllegalArgumentException(
                    "Spectrum must contain exactly " + BANDS + " values."
            );
        }

        double sum = 0.5 * spectrum[0]
                + 0.5 * spectrum[BANDS - 1];

        for (int i = 1; i < BANDS - 1; i++) {
            sum += spectrum[i];
        }

        return sum * WL_STEP;
    }

    /**
     * Generates a 24-hour series.
     * Every sample represents the START of a time step.
     * The timestep must divide 24 hours exactly.
     */
    public List<SunlightSample> generateDay(
            WeatherPreset weather,
            int stepMinutes
    ) {
        Objects.requireNonNull(weather, "Weather preset cannot be null.");
        validateStepMinutes(stepMinutes);

        List<SunlightSample> day = new ArrayList<>(
                1440 / stepMinutes
        );

        for (int minute = 0; minute < 1440; minute += stepMinutes) {
            double hour = minute / 60.0;
            day.add(generateSample(weather, hour));
        }

        return List.copyOf(day);
    }

    /**
     * Estimates natural DLI from fromHour up to, but not including,
     * endHour. Uses the same start-of-step sampling convention.
     *
     * Forecast assumes future weather is known perfectly.
     */
    public double forecastNaturalDli(
            WeatherPreset weather,
            double fromHour,
            double endHour,
            int stepMinutes
    ) {
        Objects.requireNonNull(weather, "Weather preset cannot be null.");
        validateStepMinutes(stepMinutes);

        if (!Double.isFinite(fromHour)
                || !Double.isFinite(endHour)
                || fromHour < 0.0
                || endHour > 24.0
                || endHour < fromHour) {
            throw new IllegalArgumentException(
                    "Require 0 <= fromHour <= endHour <= 24."
            );
        }

        double stepHours = stepMinutes / 60.0;
        double dli = 0.0;

        for (double hour = fromHour;
             hour < endHour - 1e-9;
             hour += stepHours) {

            // Clip the final interval so we never integrate past endHour.
            double durationHours = Math.min(
                    stepHours,
                    endHour - hour
            );

            double ppfd = generateSample(weather, hour).ppfd();

            dli += ppfd * durationHours * 3600.0 / 1.0e6;
        }

        return dli;
    }

    private static void validateStepMinutes(int stepMinutes) {
        if (stepMinutes <= 0 || 1440 % stepMinutes != 0) {
            throw new IllegalArgumentException(
                    "stepMinutes must be positive and divide 1440 exactly."
            );
        }
    }
}
