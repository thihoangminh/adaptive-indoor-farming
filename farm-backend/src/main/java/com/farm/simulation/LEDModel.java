package com.farm.simulation;
// import java.util.Arrays;

/**
 * Simulates supplemental LED lighting for an indoor-farming model.
 *
 * All outputs are simulated assumptions, not measured fixture data.
 *
 * Spectral convention:
 * - 16 wavelength samples from 400 to 700 nm at 20 nm intervals.
 * - Spectrum values are in umol m^-2 s^-1 nm^-1.
 * - Total PPFD is calculated using trapezoidal integration.
 *
 * Supports:
 * 1. Overall intensity control using a default red/blue spectrum.
 * 2. Custom wavelength-level spectral output, subject to a total PPFD cap.
 *
 * Requires Java 16+ and SunlightGenerator.java.
 */
public class LEDModel {

    private final double maxPpfd;
    private final double areaM2;
    private final double efficacyLowDrive;
    private final double droop;

    // Default spectral shape, normalized so its integral equals 1.
    private final double[] defaultShape;

    // Current spectral photon flux density at each wavelength.
    private double[] spectrum;

    public LEDModel(
            double maxPpfd,
            double areaM2,
            double efficacyLowDrive,
            double droop
    ) {
        if (!Double.isFinite(maxPpfd) || maxPpfd <= 0.0) {
            throw new IllegalArgumentException(
                    "Maximum PPFD must be finite and positive."
            );
        }
        if (!Double.isFinite(areaM2) || areaM2 <= 0.0) {
            throw new IllegalArgumentException(
                    "Lit area must be finite and positive."
            );
        }
        if (!Double.isFinite(efficacyLowDrive) || efficacyLowDrive <= 0.0) {
            throw new IllegalArgumentException(
                    "LED efficacy must be finite and positive."
            );
        }
        if (!Double.isFinite(droop) || droop < 0.0 || droop > 1.0) {
            throw new IllegalArgumentException(
                    "Droop must be between 0 and 1."
            );
        }

        this.maxPpfd = maxPpfd;
        this.areaM2 = areaM2;
        this.efficacyLowDrive = efficacyLowDrive;
        this.droop = droop;

        this.defaultShape = buildEmissionShape();
        this.spectrum = new double[SunlightGenerator.BANDS];
    }

    /**
     * Defaults:
     * 300 umol m^-2 s^-1 maximum PPFD,
     * 1 m^2 lit area,
     * 2.8 umol/J low-drive efficacy,
     * 15% efficacy droop at full output.
     */
    public LEDModel() {
        this(300.0, 1.0, 2.8, 0.15);
    }

    /**
     * Creates a simplified blue (~450 nm) and red (~660 nm) spectrum.
     * The resulting spectrum is normalized by trapezoidal integration.
     */
    private static double[] buildEmissionShape() {
        double[] shape = new double[SunlightGenerator.BANDS];

        for (int i = 0; i < shape.length; i++) {
            double wl = SunlightGenerator.wavelength(i);

            double blue = 0.30
                    * Math.exp(-Math.pow((wl - 450.0) / 20.0, 2));

            double red = 0.70
                    * Math.exp(-Math.pow((wl - 660.0) / 25.0, 2));

            shape[i] = blue + red + 0.002;
        }

        double integral = SunlightGenerator.integrateSpectrum(shape);

        for (int i = 0; i < shape.length; i++) {
            shape[i] /= integral;
        }

        return shape;
    }

    /**
     * Sets overall intensity from 0 to 1 using the default spectrum.
     * Values outside the range are clamped.
     *
     * Returns the applied intensity fraction.
     */
    public double setIntensity(double fraction) {
        if (!Double.isFinite(fraction)) {
            throw new IllegalArgumentException(
                    "Intensity must be finite."
            );
        }

        double applied = clamp(fraction, 0.0, 1.0);

        for (int i = 0; i < spectrum.length; i++) {
            spectrum[i] = maxPpfd * applied * defaultShape[i];
        }

        return applied;
    }

    /**
     * Requests a custom spectrum.
     *
     * Each array entry is a spectral photon flux density in
     * umol m^-2 s^-1 nm^-1 at the corresponding wavelength.
     *
     * If the requested integrated PPFD exceeds maxPpfd, the entire
     * spectrum is scaled proportionally to enforce the total-output cap.
     *
     * Returns the actual integrated PPFD after limiting.
     */
    public double setSpectrumDensity(double[] requestedSpectrum) {
        validateSpectrum(requestedSpectrum);

        double requestedPpfd =
                SunlightGenerator.integrateSpectrum(requestedSpectrum);

        double scale = requestedPpfd > maxPpfd
                ? maxPpfd / requestedPpfd
                : 1.0;

        for (int i = 0; i < spectrum.length; i++) {
            spectrum[i] = requestedSpectrum[i] * scale;
        }

        return getSupplementalPpfd();
    }

    private static void validateSpectrum(double[] values) {
        if (values == null || values.length != SunlightGenerator.BANDS) {
            throw new IllegalArgumentException(
                    "Spectrum must contain exactly "
                            + SunlightGenerator.BANDS + " values."
            );
        }

        for (double value : values) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        "Spectrum values must be finite and non-negative."
                );
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public double getMaxPpfd() {
        return maxPpfd;
    }

    public double getIntensity() {
        return clamp(getSupplementalPpfd() / maxPpfd, 0.0, 1.0);
    }

    /** Total supplemental PPFD at the canopy, in umol m^-2 s^-1. */
    public double getSupplementalPpfd() {
        return SunlightGenerator.integrateSpectrum(spectrum);
    }

    /** Returns a defensive copy of the current spectrum. */
    public double[] getSpectrum() {
        return spectrum.clone();
    }

    /**
     * Electrical power in watts.
     *
     * The efficacy curve is a simplified assumed model:
     * efficacy = low-drive efficacy * (1 - droop * intensity).
     */
    public double getPowerWatts() {
        double ppfd = getSupplementalPpfd();

        if (ppfd <= 0.0) {
            return 0.0;
        }

        double intensity = getIntensity();
        double efficacy = efficacyLowDrive * (1.0 - droop * intensity);

        // PPFD * area gives umol/s; divide by umol/J to obtain J/s = W.
        return (ppfd * areaM2) / efficacy;
    }

    /**
     * Approximate RGB display colour of the CURRENT spectrum.
     * This is a visual cue, not a physically accurate colour rendering.
     */
    public int[] getDisplayColorRgb() {
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;

        if (getSupplementalPpfd() <= 0.0) {
            return new int[] {0, 0, 0};
        }

        for (int i = 0; i < spectrum.length; i++) {
            // Trapezoidal integration gives half weight to endpoints.
            double integrationWeight =
                    (i == 0 || i == spectrum.length - 1) ? 0.5 : 1.0;

            double[] rgb = wavelengthToRgb(
                    SunlightGenerator.wavelength(i)
            );

            double contribution = spectrum[i] * integrationWeight;

            red += rgb[0] * contribution;
            green += rgb[1] * contribution;
            blue += rgb[2] * contribution;
        }

        double max = Math.max(red, Math.max(green, blue));

        if (max <= 0.0) {
            return new int[] {0, 0, 0};
        }

        return new int[] {
                (int) Math.round(255.0 * red / max),
                (int) Math.round(255.0 * green / max),
                (int) Math.round(255.0 * blue / max)
        };
    }

    /**
     * Rough visible-wavelength to RGB mapping, with channels in [0,1].
     */
    private static double[] wavelengthToRgb(double wl) {
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;

        if (wl < 440.0) {
            r = (440.0 - wl) / 60.0;
            b = 1.0;
        } else if (wl < 490.0) {
            g = (wl - 440.0) / 50.0;
            b = 1.0;
        } else if (wl < 510.0) {
            g = 1.0;
            b = (510.0 - wl) / 20.0;
        } else if (wl < 580.0) {
            r = (wl - 510.0) / 70.0;
            g = 1.0;
        } else if (wl < 645.0) {
            r = 1.0;
            g = (645.0 - wl) / 65.0;
        } else {
            r = 1.0;
        }

        return new double[] {r, g, b};
    }
}
