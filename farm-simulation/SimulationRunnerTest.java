 public class SimulationRunnerTest {
    public static void main(String[] args) {
        SimulationRunner runner = new SimulationRunner();

        for (SimulationRunner.DayResult result : runner.runAll()) {
            SimulationRunner.checkConfiguration(result);
        }

        System.out.println("\nAll configuration checks passed!");
    }
}
