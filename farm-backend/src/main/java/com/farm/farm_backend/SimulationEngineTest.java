package com.farm.farm_backend;

import com.farm.simulation.SimulationRunner;
import com.farm.simulation.SunlightGenerator;
import com.farm.simulation.SimulationRunner.DayResult;

public class SimulationEngineTest {

    public static void main(String[] args) {

        SimulationRunner runner = new SimulationRunner();

        for (SunlightGenerator.WeatherPreset weather
                : SunlightGenerator.WeatherPreset.values()) {

            DayResult result = runner.run(weather);

            System.out.println("\n===== " + weather + " =====");

            System.out.println("Adaptive energy: "
                    + result.adaptive().energyKWh() + " kWh");

            System.out.println("Fixed energy: "
                    + result.fixed().energyKWh() + " kWh");

            System.out.println("Adaptive DLI: "
                    + result.adaptive().dli());

            System.out.println("Fixed DLI: "
                    + result.fixed().dli());

            System.out.println("Energy saved: "
                    + result.comparison().kWhSaved() + " kWh");

            System.out.println("Number of time steps: "
                    + result.steps().size());
        }
    }
}