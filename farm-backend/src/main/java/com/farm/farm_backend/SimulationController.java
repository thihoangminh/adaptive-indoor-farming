
package com.farm.farm_backend;


import com.farm.simulation.SimulationRunner;
import com.farm.simulation.SunlightGenerator;
import com.farm.simulation.SimulationRunner.DayResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
@CrossOrigin(origins = "http://127.0.0.1:5173")
@RestController
public class SimulationController {

    private final SimulationRunner runner = new SimulationRunner();

    @GetMapping("/api/simulation")
    public DayResult getSimulation(
            @RequestParam(defaultValue = "SUNNY") String weather) {

        SunlightGenerator.WeatherPreset preset =
                SunlightGenerator.WeatherPreset.valueOf(
                        weather.toUpperCase()
                );

        return runner.run(preset);
    }
}
