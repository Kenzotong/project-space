package org.matsim.project.drtRequestPatternIdentification.prepare;

import net.opengis.ows10.SectionsType;
import org.apache.commons.collections.map.HashedMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;

public class createTravelTime {
    public static void main(String[] args) {
        // Create scenario based on config file
        String configPath = "D:/github/project-space/scenarios/vulkaneifel/config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
        Network network = scenario.getNetwork();

        // Create router (based on free speed)
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;
        System.out.println("DRT setups: alpha, beta, maxWaitTime, stopDuration " + alpha + ", " + beta + ", " + maxWaitTime + ", " + stopDuration);

        List<TripStructureUtils.Trip> drtTripSet = createDrtTripsSet.getDrtTripSet();
        System.out.println(drtTripSet.size());

        //create maps to store travel time
        Map<Link,Map<Link, Double>> linkPairAndTravelTime = new HashMap<>();
        Map <Link, Double> travelTimeMap = new HashMap<>();


        //get drt trip set 1
        for (TripStructureUtils.Trip trip1 : drtTripSet) {
            //get drt trip set 2
            for (TripStructureUtils.Trip trip2 : drtTripSet) {

                // get OD link
                // When link id is written in the plan
//                Link fromLink1 = network.getLinks().get(trip1.getOriginActivity().getLinkId());
//                Link toLink1 = network.getLinks().get(trip1.getDestinationActivity().getLinkId());
//                Link fromLink2 = network.getLinks().get(trip2.getOriginActivity().getLinkId());
//                Link toLink2 = network.getLinks().get(trip2.getDestinationActivity().getLinkId());

                // When link id is not provided (coordinate is provided instead)
                Link fromLink1 = NetworkUtils.getNearestLink(network, trip1.getOriginActivity().getCoord());
                Link toLink1 = NetworkUtils.getNearestLink(network, trip1.getDestinationActivity().getCoord());
                Link fromLink2 = NetworkUtils.getNearestLink(network, trip2.getOriginActivity().getCoord());
                Link toLink2 = NetworkUtils.getNearestLink(network, trip2.getDestinationActivity().getCoord());

                int numberOfPairs = 0;

                //calculate travel time
                //o1 to o2
                VrpPathWithTravelData pathO1ToO2 = VrpPaths.calcAndCreatePath(fromLink1, fromLink2, 0, router, travelTime);
                double tripTimeO1ToO2 = pathO1ToO2.getTravelTime();
                linkPairAndTravelTime.put(fromLink1, travelTimeMap);
                travelTimeMap.put(fromLink2,tripTimeO1ToO2);

                //o2 to o1
                VrpPathWithTravelData pathO2ToO1 = VrpPaths.calcAndCreatePath(fromLink2, fromLink1, 0, router, travelTime);
                double tripTimeO2ToO1 = pathO2ToO1.getTravelTime();
                linkPairAndTravelTime.put(fromLink2,travelTimeMap);
                travelTimeMap.put(fromLink1,tripTimeO2ToO1);

                //o2 to d1
                VrpPathWithTravelData pathO2ToD1 = VrpPaths.calcAndCreatePath(fromLink2, toLink1, 0, router, travelTime);
                double tripTimeO2ToD1 = pathO2ToD1.getTravelTime();
                linkPairAndTravelTime.put(fromLink2,travelTimeMap);
                travelTimeMap.put(toLink1,tripTimeO2ToD1);

                //o2 to d2
                VrpPathWithTravelData pathO2ToD2 = VrpPaths.calcAndCreatePath(fromLink2, toLink2, 0, router, travelTime);
                double tripTimeO2ToD2 = pathO2ToD2.getTravelTime();
                linkPairAndTravelTime.put(fromLink2,travelTimeMap);
                travelTimeMap.put(toLink2,tripTimeO2ToD2);

                //o1 to d1
                VrpPathWithTravelData pathO1ToD1 = VrpPaths.calcAndCreatePath(fromLink1, toLink1, 0, router, travelTime);
                double tripTimeO1ToD1 = pathO1ToD1.getTravelTime();
                linkPairAndTravelTime.put(fromLink1,travelTimeMap);
                travelTimeMap.put(toLink1,tripTimeO1ToD1);

                //o1 to d2
                VrpPathWithTravelData pathO1ToD2 = VrpPaths.calcAndCreatePath(fromLink1, toLink2, 0, router, travelTime);
                double tripTimeO1ToD2 = pathO1ToD2.getTravelTime();
                linkPairAndTravelTime.put(fromLink1,travelTimeMap);
                travelTimeMap.put(toLink2,tripTimeO1ToD2);

                //d1 to d2
                VrpPathWithTravelData pathD1ToD2 = VrpPaths.calcAndCreatePath(toLink1, toLink2, 0, router, travelTime);
                double tripTimeD1ToD2 = pathD1ToD2.getTravelTime();
                linkPairAndTravelTime.put(toLink1,travelTimeMap);
                travelTimeMap.put(toLink2,tripTimeD1ToD2);

                //d2 to d1
                VrpPathWithTravelData pathD2ToD1 = VrpPaths.calcAndCreatePath(toLink2, toLink1, 0, router, travelTime);
                double tripTimeD2ToD1 = pathD2ToD1.getTravelTime();
                linkPairAndTravelTime.put(toLink2,travelTimeMap);
                travelTimeMap.put(toLink1,tripTimeD2ToD1);

            }
        }

        System.out.println(travelTimeMap.size());

    }
}
