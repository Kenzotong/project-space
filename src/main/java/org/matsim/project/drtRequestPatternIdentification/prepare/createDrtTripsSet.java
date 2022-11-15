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

public class createDrtTripsSet {
    public static void main(String[] args){
        // Create scenario based on config file
        String configPath = "D:/github/project-space/scenarios/vulkaneifel/config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Population population = scenario.getPopulation();

        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

        //所有人使用drt的trip合集
        for(Person person : population.getPersons().values()) {
            trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip drtTrip : trips) {
                //判断主要交通工具 是不是drt
                if (mainModeIdentifier.identifyMainMode(drtTrip.getTripElements()).equals(TransportMode.drt)) {
                    //若是，则把这段trip添加进drt的trip合集中
                    drtTripSet.add(drtTrip);
                }
            }
        }
    }

    //all trips of a person
    public static List<TripStructureUtils.Trip> trips = new ArrayList<>();

    public static List<TripStructureUtils.Trip> drtTripSet = new ArrayList<>();

    public static List<TripStructureUtils.Trip> getDrtTripSet(){
        return drtTripSet;
    }
}
