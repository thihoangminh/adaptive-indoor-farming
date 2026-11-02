package com.farm.simulation;
import java.util.ArrayList;
import java.util.List;

/**
 * SimulationRunner
 *
 * Runs adaptive vs fixed LED lighting over one simulated day under identical
 * natural sunlight, and RETURNS the results as objects (nothing is printed).
 * The website or any other caller can read the values and draw its own graphs.
 *
 * All sunlight and LED behaviour are simulated, not measured.
 *
 * Uses CostAnalyser (British spelling) to match your version of the class.
 */
public class SimulationRunner {

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    /** Everything that happened in ONE time step (for graphs and the decision log). */
    public record StepRecord(
            double hour,                   // start of the step (decimal hours)
            double naturalPpfd,            // umol m^-2 s^-1
            double[] sunSpectrum,          // umol m^-2 s^-1 nm^-1 on the shared grid

            // Adaptive strategy
            double adaptiveLedFraction,    // 0..1
            double adaptiveLedPpfd,
            double adaptivePowerWatts,
            double adaptiveAccumulatedDli, // mol m^-2 AFTER this step
            double[] adaptiveLedSpectrum,
            String decisionReason,         // why the controller chose this

            // Fixed baseline
            double fixedLedFraction,
            double fixedLedPpfd,
            double fixedPowerWatts,
            double fixedAccumulatedDli) { }

    /** Totals for one strategy over the day. */
    public record StrategyResult(
            double energyKWh,
            double cost,
            double dli,            // total light received, mol m^-2
            double dliGap,         // how far below the target (0 if reached)
            boolean targetReached) { }

    /** The full result for one weather preset. */
   /** The full result for one weather preset, including reproducibility settings. */
public record DayResult(
        SunlightGenerator.WeatherPreset weather,
        double targetDli,
        int stepMinutes,
        double windowEnd,
        double fixedStartHour,
        double fixedIntensity,
        double minSwitchOnPpfd,
        double electricityPrice,
        String currency,
        StrategyResult adaptive,
        StrategyResult fixed,
        CostAnalyser.Comparison comparison,
        List<StepRecord> steps) {

    public DayResult {
        steps = List.copyOf(steps);
    }
}

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    private final SunlightGenerator sun;
    private final int stepMinutes;
    private final double targetDli;          // mol m^-2 day^-1
    private final double windowEnd;          // lights allowed until this hour
    private final double fixedStartHour;     // baseline switches on at this hour
    private final double fixedIntensity;     // baseline LED level, 0..1
    private final double minSwitchOnPpfd;    // controller ignores smaller requests
    private final double electricityPrice;   // per kWh
    private final String currency;           // label only

    public SimulationRunner(SunlightGenerator sun, int stepMinutes, double targetDli,
                            double windowEnd, double fixedStartHour, double fixedIntensity,
                            double minSwitchOnPpfd, double electricityPrice, String currency) {
        this.sun = sun;
        this.stepMinutes = stepMinutes;
        this.targetDli = targetDli;
        this.windowEnd = windowEnd;
        this.fixedStartHour = fixedStartHour;
        this.fixedIntensity = fixedIntensity;
        this.minSwitchOnPpfd = minSwitchOnPpfd;
        this.electricityPrice = electricityPrice;
        this.currency = currency;
    }

    /** Same defaults as the console demo. */
    public SimulationRunner() {
        this(new SunlightGenerator(), 5, 17.0, 22.0, 6.0, 0.60, 5.0, 0.10, "USD");
    }

    // ------------------------------------------------------------------
    // Running the simulation
    // ------------------------------------------------------------------

    /** Run every weather preset (Sunny, Cloudy, Rainy). */
    public List<DayResult> runAll() {
        List<DayResult> results = new ArrayList<>();
        for (SunlightGenerator.WeatherPreset weather : SunlightGenerator.WeatherPreset.values()) {
            results.add(run(weather));
        }
        return results;
    }

    /** Run one simulated day for the given weather. */
    public DayResult run(SunlightGenerator.WeatherPreset weather) {
        final double stepSeconds = stepMinutes * 60.0;

        // Fresh objects each run so days never affect each other.
        LEDModel adaptiveLed = new LEDModel();
        LEDModel fixedLed = new LEDModel();
        LightingController controller = new LightingController(
                targetDli, adaptiveLed.getMaxPpfd(), windowEnd, minSwitchOnPpfd);
        CostAnalyser cost = new CostAnalyser(electricityPrice, currency);

        double fixedDli = 0.0;
        List<StepRecord> steps = new ArrayList<>();

        // One shared natural-light day for BOTH strategies (fair comparison).
        List<SunlightGenerator.SunlightSample> day = sun.generateDay(weather, stepMinutes);

        for (SunlightGenerator.SunlightSample s : day) {
            double hour = s.hour();
            double naturalPpfd = s.ppfd();

            // ----- Adaptive strategy -----
            String reason;
            if (hour < windowEnd) {
                double forecast = sun.forecastNaturalDli(weather, hour, windowEnd, stepMinutes);
                LightingController.Decision decision =
                        controller.decide(naturalPpfd, hour, forecast);
                adaptiveLed.setIntensity(decision.ledFraction());
                reason = decision.reason();
            } else {
                adaptiveLed.setIntensity(0.0); // supplemental light disabled after the window
                reason = "Outside the lighting window - LED off.";
            }

            controller.commitStep(naturalPpfd, adaptiveLed.getSupplementalPpfd(), stepSeconds);
            cost.recordAdaptive(adaptiveLed.getPowerWatts(), stepSeconds);

            // ----- Fixed-lighting baseline -----
            boolean withinWindow = hour >= fixedStartHour && hour < windowEnd;
            fixedLed.setIntensity(withinWindow ? fixedIntensity : 0.0);
            fixedDli += (naturalPpfd + fixedLed.getSupplementalPpfd()) * stepSeconds / 1.0e6;
            cost.recordBaseline(fixedLed.getPowerWatts(), stepSeconds);

            // ----- Store this step for graphs and the decision log -----
            steps.add(new StepRecord(
                    hour, naturalPpfd, s.spectrum().clone(),
                    adaptiveLed.getIntensity(), adaptiveLed.getSupplementalPpfd(),
                    adaptiveLed.getPowerWatts(), controller.getAccumulatedDli(),
                    adaptiveLed.getSpectrum(), reason,
                    fixedLed.getIntensity(), fixedLed.getSupplementalPpfd(),
                    fixedLed.getPowerWatts(), fixedDli));
        }

        // ----- Totals and comparison (from recorded results only) -----
        CostAnalyser.Comparison comparison = cost.compare();
        double adaptiveDli = controller.getAccumulatedDli();

        StrategyResult adaptive = new StrategyResult(
                comparison.adaptiveKWh(), comparison.adaptiveCost(), adaptiveDli,
                Math.max(0.0, targetDli - adaptiveDli), adaptiveDli >= targetDli);

        StrategyResult fixed = new StrategyResult(
                comparison.baselineKWh(), comparison.baselineCost(), fixedDli,
                Math.max(0.0, targetDli - fixedDli), fixedDli >= targetDli);
        

        return new DayResult(
        weather,
        targetDli,
        stepMinutes,
        windowEnd,
        fixedStartHour,
        fixedIntensity,
        minSwitchOnPpfd,
        electricityPrice,
        currency,
        adaptive,
        fixed,
        comparison,
        steps
        );
        
    }
   public static void checkConfiguration(DayResult result) {
    System.out.println("\nConfiguration check: " + result.weather());

    check("Timestep is 5 minutes",
            result.stepMinutes() == 5);

    check("Lighting window ends at 22:00",
            result.windowEnd() == 22.0);

    check("Fixed lighting starts at 06:00",
            result.fixedStartHour() == 6.0);

    check("Fixed LED intensity is 60%",
            Math.abs(result.fixedIntensity() - 0.60) < 1e-9);

    check("Electricity tariff is 0.10 USD/kWh",
            Math.abs(result.electricityPrice() - 0.10) < 1e-9
                    && "USD".equals(result.currency()));
}

private static void check(String description, boolean passed) {
    System.out.println((passed ? "[PASS] " : "[FAIL] ") + description);

    if (!passed) {
        throw new AssertionError("Configuration check failed: " + description);
    }
} 
}
