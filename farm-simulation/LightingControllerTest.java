
public class LightingControllerTest {

    public static void main(String[] args) {
        testTargetAlreadyReached();
        testTargetUnreachable();
        System.out.println("All controller tests passed.");
    }

    private static void testTargetAlreadyReached() {
        LightingController controller =
                new LightingController(10.0, 300.0);

        // Simulate a crop that has already accumulated its target.
        controller.commitStep(100.0, 0.0, 360_000.0);

        LightingController.Decision d =
                controller.decide(100.0, 10.0, 0.0);

        if (d.ledFraction() != 0.0) {
            throw new AssertionError(
                    "LED should be off after reaching the target.");
        }
    }

    private static void testTargetUnreachable() {
        LightingController controller =
                new LightingController(20.0, 300.0, 22.0, 5.0);

        LightingController.Decision d =
                controller.decide(0.0, 21.0, 0.0);

        if (d.ledFraction() != 1.0) {
            throw new AssertionError(
                    "LED should run at maximum when required PPFD exceeds capacity.");
        }

        if (d.targetReachable()) {
            throw new AssertionError(
                    "Target should be flagged unreachable.");
        }
    }
}
