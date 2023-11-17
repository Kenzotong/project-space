package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.core.config.Config;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;

import java.util.ArrayList;
import java.util.List;
//@Deprecated
public class DrtTripsSet {
    //    public static void main(String[] args){
//        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
//        if (args.length != 0) {
//            configPath = args[0];
//        }
//        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
//        System.out.println(getDrtDemandsSet(config));
//    }

    public static List<TripStructureUtils.Trip> getDrtTripsSet(Config config){
        List<TripStructureUtils.Trip> drtTrips = new ArrayList<>();

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Population population = scenario.getPopulation();
        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

        for (Person person : population.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(TransportMode.drt)) {
                    drtTrips.add(trip);
                }
            }
        }
        return drtTrips;
    }
}
