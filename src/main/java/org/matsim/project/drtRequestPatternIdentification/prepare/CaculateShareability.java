package org.matsim.project.drtRequestPatternIdentification.prepare;


import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;
import java.util.Map;

public class CaculateShareability {

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

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;
        System.out.println("DRT setups: alpha, beta, maxWaitTime, stopDuration " + alpha + ", " + beta + ", " + maxWaitTime + ", " + stopDuration);

        int numberOfShare = 0;

        List<TripStructureUtils.Trip> drtTripSet = DrtTripsSet.getDrtTripSet();
        //number of drt trips
        System.out.println(drtTripSet.size());

        //get trip time map
        Map<Link,Map<Link,Double>> tripTimeMap = TripTimeSet.getTripTimeSet();
        System.out.println(tripTimeMap.size());

        //get drt trip set 1
        for (TripStructureUtils.Trip trip1 : drtTripSet) {
            //get drt trip set 2
            for (TripStructureUtils.Trip trip2 : drtTripSet) {

                double departureTime1 = trip1.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
                double departureTime2 = trip2.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);

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

                //determine if it is shareable
                //if trip 1 is the same as trip 2
                if (trip1 == trip2) {
                    continue;
                }
                //o1,o2,d1,d2
                if (departureTime2 + maxWaitTime > departureTime1 + tripTimeMap.get(fromLink1).get(fromLink2)) {
                    if (departureTime1 + alpha * tripTimeMap.get(fromLink1).get(fromLink2) + beta > departureTime1 + tripTimeMap.get(fromLink1).get(fromLink2) + tripTimeMap.get(fromLink2).get(toLink1)) {
                        if (departureTime2 + (alpha * tripTimeMap.get(fromLink2).get(toLink2)) + beta > departureTime1 + tripTimeMap.get(fromLink1).get(fromLink2) + tripTimeMap.get(fromLink2).get(toLink1) + tripTimeMap.get(toLink1).get(toLink2)) {
                            numberOfShare ++;
                            continue;
                        }
                    }
                }
                //o1,o2,d2,d1
                if (departureTime2 + maxWaitTime > departureTime1 + tripTimeMap.get(fromLink1).get(fromLink2)) {
                    if (departureTime2 + alpha * tripTimeMap.get(fromLink2).get(toLink2) + beta > departureTime1 + tripTimeMap.get(fromLink1).get(fromLink2) + tripTimeMap.get(fromLink2).get(toLink2)) {
                        if (departureTime1 + alpha * tripTimeMap.get(fromLink1).get(toLink1) + beta > departureTime1 + tripTimeMap.get(fromLink1).get(fromLink2) + tripTimeMap.get(fromLink2).get(toLink2) + tripTimeMap.get(toLink2).get(toLink1)) {
                            numberOfShare ++;
                            continue;
                        }
                    }
                    //o2,o1,d1,d2
                    if (departureTime1 + maxWaitTime > departureTime2 + tripTimeMap.get(fromLink2).get(toLink1)) {
                        if (departureTime1 + alpha * tripTimeMap.get(fromLink1).get(toLink1) + beta > departureTime2 + tripTimeMap.get(fromLink2).get(fromLink1) + tripTimeMap.get(fromLink1).get(toLink1)) {
                            if (departureTime2 + alpha * tripTimeMap.get(fromLink2).get(toLink2) + beta > departureTime2 + tripTimeMap.get(fromLink2).get(fromLink1) + tripTimeMap.get(fromLink1).get(toLink1) + tripTimeMap.get(toLink1).get(toLink2)) {
                                numberOfShare ++;
                                continue;
                            }
                        }
                    }
                    //o2,o1,d2,d1
                    if (departureTime1 + maxWaitTime > departureTime2 + tripTimeMap.get(fromLink2).get(fromLink1)) {
                        if (departureTime2 + alpha * tripTimeMap.get(fromLink2).get(toLink2) + beta > departureTime2 + tripTimeMap.get(fromLink2).get(fromLink1) + tripTimeMap.get(fromLink1).get(toLink2)) {
                            if (departureTime1 + alpha * tripTimeMap.get(fromLink1).get(toLink1) + beta > departureTime2 + tripTimeMap.get(fromLink2).get(fromLink1) + tripTimeMap.get(fromLink1).get(toLink2) + tripTimeMap.get(toLink2).get(toLink1)) {
                                numberOfShare ++;
                            }
                        }
                    }
                }
            }
        }

        System.out.println("number of pairs is: " + numberOfShare);

    }
}

