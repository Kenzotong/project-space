package org.matsim.project.drtRequestPatternIdentification.prepare;


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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CaculateShareability {

    public static void main(String[] args) {
        // Create scenario based on config file
        String configPath = "D:\\Thesis\\mielec\\mielec-scenario\\mielec_drt_config.xml";
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

        int numberOfShare = 0;

        // Get drt trip set
        List<TripStructureUtils.Trip> drtTripSet = DrtTripsSet.getDrtTripSet();
        System.out.println("number of trips is: " + drtTripSet.size());

        // Get trip time map
//        Map<Link,Map<Link,Double>> tripTimeMap = TripTimeSet.getTripTimeSet();
//        System.out.println(tripTimeMap.size());

        // Get drt trip set 1
        for (TripStructureUtils.Trip trip1 : drtTripSet) {
            // Get drt trip set 2
            for (TripStructureUtils.Trip trip2 : drtTripSet) {

                double departureTime1 = trip1.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
                double departureTime2 = trip2.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);

                // get OD link
                // When link id is written in the plan
                Link fromLink1 = network.getLinks().get(trip1.getOriginActivity().getLinkId());
                Link toLink1 = network.getLinks().get(trip1.getDestinationActivity().getLinkId());
                Link fromLink2 = network.getLinks().get(trip2.getOriginActivity().getLinkId());
                Link toLink2 = network.getLinks().get(trip2.getDestinationActivity().getLinkId());

                // When link id is not provided (coordinate is provided instead)
//                Link fromLink1 = NetworkUtils.getNearestLink(network, trip1.getOriginActivity().getCoord());
//                Link toLink1 = NetworkUtils.getNearestLink(network, trip1.getDestinationActivity().getCoord());
//                Link fromLink2 = NetworkUtils.getNearestLink(network, trip2.getOriginActivity().getCoord());
//                Link toLink2 = NetworkUtils.getNearestLink(network, trip2.getDestinationActivity().getCoord());

                // Trip time from a link to another link
//                double tripTimeO1ToO2 = tripTimeMap.get(fromLink1).get(fromLink2);
//                double tripTimeO2ToD1 = tripTimeMap.get(fromLink2).get(toLink1);
//                double tripTimeD1ToD2 = tripTimeMap.get(toLink1).get(toLink2);
//                double tripTimeO2ToD2 = tripTimeMap.get(fromLink2).get(toLink2);
//                double tripTimeD2ToD1 = tripTimeMap.get(toLink2).get(toLink1);
//                double tripTimeO2ToO1 = tripTimeMap.get(fromLink2).get(fromLink1);
//                double tripTimeO1ToD1 = tripTimeMap.get(fromLink1).get(toLink1);
//                double tripTimeO1ToD2 = tripTimeMap.get(fromLink1).get(toLink2);

                // Calculate travel time
                // o1 to o2
                VrpPathWithTravelData pathO1ToO2 = VrpPaths.calcAndCreatePath(fromLink1, fromLink2, 0, router, travelTime);
                double tripTimeO1ToO2 = pathO1ToO2.getTravelTime();

                // o2 to o1
                VrpPathWithTravelData pathO2ToO1 = VrpPaths.calcAndCreatePath(fromLink2, fromLink1, 0, router, travelTime);
                double tripTimeO2ToO1 = pathO2ToO1.getTravelTime();

                // o2 to d1
                VrpPathWithTravelData pathO2ToD1 = VrpPaths.calcAndCreatePath(fromLink2, toLink1, 0, router, travelTime);
                double tripTimeO2ToD1 = pathO2ToD1.getTravelTime();

                // o2 to d2
                VrpPathWithTravelData pathO2ToD2 = VrpPaths.calcAndCreatePath(fromLink2, toLink2, 0, router, travelTime);
                double tripTimeO2ToD2 = pathO2ToD2.getTravelTime();

                // o1 to d1
                VrpPathWithTravelData pathO1ToD1 = VrpPaths.calcAndCreatePath(fromLink1, toLink1, 0, router, travelTime);
                double tripTimeO1ToD1 = pathO1ToD1.getTravelTime();

                // o1 to d2
                VrpPathWithTravelData pathO1ToD2 = VrpPaths.calcAndCreatePath(fromLink1, toLink2, 0, router, travelTime);
                double tripTimeO1ToD2 = pathO1ToD2.getTravelTime();

                // d1 to d2
                VrpPathWithTravelData pathD1ToD2 = VrpPaths.calcAndCreatePath(toLink1, toLink2, 0, router, travelTime);
                double tripTimeD1ToD2 = pathD1ToD2.getTravelTime();

                // d2 to d1
                VrpPathWithTravelData pathD2ToD1 = VrpPaths.calcAndCreatePath(toLink2, toLink1, 0, router, travelTime);
                double tripTimeD2ToD1 = pathD2ToD1.getTravelTime();

                // Determine if it is shareable
                // If trip 1 is the same as trip 2
                if (trip1 == trip2) {
                    continue;
                }

                // o1,o2,d1,d2
                //O2的最晚出发时间要大于O1到O2的最晚到达时间
                if (departureTime2 + maxWaitTime > departureTime1 + maxWaitTime + tripTimeO1ToO2) {
                    //D1的最晚到达时间要大于O2的最晚出发时间到D1的实际到达时间
                    if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + maxWaitTime + tripTimeO2ToD1) {
                        //D2的最晚到达时间要大于D1的最晚到达时间到D2的实际到达时间
                        if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime1 + alpha * tripTimeO1ToD1 + beta + tripTimeD1ToD2) {
                            numberOfShare ++;
                            continue;
                        }
                    }
                }
                // o1,o2,d2,d1
                //O2的最晚出发时间要大于O1到O2的最晚到达时间
                if (departureTime2 + maxWaitTime > departureTime1 + maxWaitTime + tripTimeO1ToO2) {
                    //D2的最晚到达时间要大于O2的最晚出发时间到D2的实际到达时间
                    if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime2 + maxWaitTime + tripTimeO2ToD2) {
                        //D1的最晚到达时间要大于D2的最晚到达时间到D1的实际到达时间
                        if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + alpha * tripTimeO2ToD2 + beta + tripTimeD2ToD1) {
                            numberOfShare++;
                            continue;
                        }
                    }
                }
                // o2,o1,d1,d2
                //O1的最晚出发时间要大于O2到O1的最晚到达时间
                if (departureTime1 + maxWaitTime > departureTime2 + maxWaitTime + tripTimeO2ToO1) {
                    //D1的最晚到达时间要大于O1的最晚出发时间到D1的实际到达时间
                    if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime1 + maxWaitTime + tripTimeO1ToD1) {
                        //D2的最晚到达时间要大于D1的最晚出发时间到D2的实际到达时间
                        if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime1 + alpha * tripTimeO1ToD1 + beta + tripTimeD1ToD2) {
                            numberOfShare ++;
                            continue;
                        }
                    }
                }
                // o2,o1,d2,d1
                //O1的最晚出发时间要大于O2到O1的最晚到达时间
                if (departureTime1 + maxWaitTime > departureTime2 + maxWaitTime + tripTimeO2ToO1) {
                    //D2的最晚到达时间要大于O1的最晚出发时间到D2的实际到达时间
                    if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2) {
                        //D1的最晚到达时间要大于D2的最晚出发时间到D1的实际到达时间
                        if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + alpha * tripTimeO2ToD2 + beta + tripTimeD2ToD1) {
                            numberOfShare ++;
                        }
                    }
                }
            }
        }

        System.out.println("number of share pairs is: " + numberOfShare);
        System.out.println("total number of pairs is: " + (drtTripSet.size() * drtTripSet.size()- drtTripSet.size()) / 2);

    }
}

