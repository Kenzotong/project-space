package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;

public class DrtTripsSet {
    public static void main(String[] args){
        System.out.println(getDrtTripSet().size());
    }

    public static List<TripStructureUtils.Trip> getDrtTripSet(){
        List<TripStructureUtils.Trip> drtTripSet = new ArrayList<>();

        String configPath = "D:/github/project-space/scenarios/vulkaneifel/config.xml";
        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Population population = scenario.getPopulation();

        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

        // a trip set including all drt trips
        for(Person person : population.getPersons().values()) {
            // all trips from a person
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip drtTrip : trips) {
                // Determine if main traffic mode is DRT
                if (mainModeIdentifier.identifyMainMode(drtTrip.getTripElements()).equals(TransportMode.drt)) {
                    // If yes, add this trip in the trip set
                    drtTripSet.add(drtTrip);
                }
            }
        }
        return drtTripSet;
    }
}
