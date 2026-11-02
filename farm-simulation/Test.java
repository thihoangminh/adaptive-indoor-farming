
public class Test {
    public static void main(String[] args) {
        CostAnalyser analyzer = new CostAnalyser(3000, "VND");

        // Adaptive: 100 W for one hour.
        analyzer.recordAdaptive(100, 3600);

        // Baseline: 200 W for one hour.
        analyzer.recordBaseline(200, 3600);

        CostAnalyser.Comparison result = analyzer.compare();

        System.out.println(result.adaptiveKWh());       // 0.1
        System.out.println(result.baselineKWh());       // 0.2
        System.out.println(result.kWhSaved());          // 0.1
        System.out.println(result.kWhSavedPercent());   // 50.0
        System.out.println(result.adaptiveCost());      // 300.0
        System.out.println(result.costSaved());         // 300.0
        System.out.println(analyzer.getCurrency());     // VND
    }
}