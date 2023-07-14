package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
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
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;

import java.util.*;

public class DrtTripsSet {

//    public static void main(String[] args){
//        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
//        if (args.length != 0) {
//            configPath = args[0];
//        }
//        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
//        System.out.println(getDrtDemandsSet(config));
//    }

    public static List<DrtDemand> getDrtDemandsSet(Config config){
        List<DrtDemand> drtDemandsSet = new ArrayList<>();


        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        Population population = scenario.getPopulation();

        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

        List<DrtDemand> demands = new ArrayList<>();
        for (Person person : population.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(TransportMode.drt)) {
                    double departureTime = trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
                    Link fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
                    Link toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());
                    demands.add(new DrtDemand(person.getId().toString(), fromLink, toLink, departureTime));
                }
            }
        }
        return demands;
    }
}
