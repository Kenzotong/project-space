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

import java.util.List;
import java.util.Map;

public class test {

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
        int numberOfPair = 0;

        // Get drt trip set
        List<TripStructureUtils.Trip> drtTripSet = DrtTripsSet.getDrtTripSet();
        System.out.println(drtTripSet.size());


        for (TripStructureUtils.Trip trip1 : drtTripSet) {

            //get drt trip set 2
            for (TripStructureUtils.Trip trip2 : drtTripSet) {

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

                //get origin destination time
                double departureTime1 = trip1.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
                double departureTime2 = trip2.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);

                //calculate every route
                //o1 to o2
                VrpPathWithTravelData pathO1ToO2 = VrpPaths.calcAndCreatePath(fromLink1, fromLink2, departureTime1, router, travelTime);
                double tripTimeO1ToO2 = pathO1ToO2.getTravelTime();

                //o2 to o1
                VrpPathWithTravelData pathO2ToO1 = VrpPaths.calcAndCreatePath(fromLink2, fromLink1, departureTime2, router, travelTime);
                double tripTimeO2ToO1 = pathO2ToO1.getTravelTime();

                //o2 to d1
                VrpPathWithTravelData pathO2ToD1 = VrpPaths.calcAndCreatePath(fromLink2, toLink1, departureTime1 + tripTimeO1ToO2, router, travelTime);
                double tripTimeO2ToD1 = pathO2ToD1.getTravelTime();

                //o2 to d2
                VrpPathWithTravelData pathO2ToD2 = VrpPaths.calcAndCreatePath(fromLink2, toLink2, departureTime1 + tripTimeO1ToO2, router, travelTime);
                double tripTimeO2ToD2 = pathO2ToD2.getTravelTime();

                //o1 to d1
                VrpPathWithTravelData pathO1ToD1 = VrpPaths.calcAndCreatePath(fromLink1, toLink1, departureTime2 + tripTimeO2ToO1, router, travelTime);
                double tripTimeO1ToD1 = pathO1ToD1.getTravelTime();

                //o1 to d2
                VrpPathWithTravelData pathO1ToD2 = VrpPaths.calcAndCreatePath(fromLink1, toLink2, departureTime2 + tripTimeO2ToO1, router, travelTime);
                double tripTimeO1ToD2 = pathO1ToD2.getTravelTime();

                //d1 to d2, from plan o1,o2,d1,d2
                VrpPathWithTravelData pathD1ToD2_1 = VrpPaths.calcAndCreatePath(toLink1, toLink2, departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD1, router, travelTime);
                double tripTimeD1ToD2_1 = pathD1ToD2_1.getTravelTime();

                //d2 to d1, from plan o1,o2,d2,d1
                VrpPathWithTravelData pathD2ToD1_1 = VrpPaths.calcAndCreatePath(toLink2, toLink1, departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD2, router, travelTime);
                double tripTimeD2ToD1_1 = pathD2ToD1_1.getTravelTime();

                //d1 to d2, from plan o2,o1,d1,d2
                VrpPathWithTravelData pathD1ToD2_2 = VrpPaths.calcAndCreatePath(toLink1, toLink2, departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD1, router, travelTime);
                double tripTimeD1ToD2_2 = pathD1ToD2_2.getTravelTime();

                //d2 to d1, from plan o2,o1,d2,d1
                VrpPathWithTravelData pathD2ToD1_2 = VrpPaths.calcAndCreatePath(toLink2, toLink1, departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2, router, travelTime);
                double tripTimeD2ToD1_2 = pathD2ToD1_2.getTravelTime();


                //determine if it is shareable
                //if trip 1 is the same as trip 2
                if (trip1 == trip2) {
                    continue;
                }

                numberOfPair++;

                //o1,o2,d1,d2
                //实际2的出发时间在2的最大等待时间之内
                if (departureTime2 + maxWaitTime > departureTime1 + tripTimeO1ToO2) {
                    //实际1到达时间在1的最大行程时间之内
                    if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD1) {
                        //实际2的到达时间在2的最大行程时间之内
                        if (departureTime2 + (alpha * tripTimeO2ToD2) + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD1 + tripTimeD1ToD2_1) {
                            numberOfShare++;
                            continue;
                        }
                    }
                }
                //o1,o2,d2,d1
                if (departureTime2 + maxWaitTime > departureTime1 + tripTimeO1ToO2) {
                    if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD2) {
                        if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD2 + tripTimeD2ToD1_1) {
                            numberOfShare++;
                            continue;
                        }
                    }
                    //o2,o1,d1,d2
                    if (departureTime1 + maxWaitTime > departureTime2 + tripTimeO2ToO1) {
                        if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD1) {
                            if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD1 + tripTimeD1ToD2_2) {
                                numberOfShare++;
                                continue;
                            }
                        }
                    }
                    //o2,o1,d2,d1
                    if (departureTime1 + maxWaitTime > departureTime2 + tripTimeO2ToO1) {
                        if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2) {
                            if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2 + tripTimeD2ToD1_2) {
                                numberOfShare++;

                            }
                        }
                    }
                }
            }


        }

//        int totalPairs = (1+drtTripSet.size()-1)*(drtTripSet.size()-1)/2;

        System.out.println("number of sharing is: " + numberOfShare);
        System.out.println("number of pairs is: " + numberOfPair);
        System.out.println("sharablitity rate is: " + numberOfShare/(double)numberOfPair);
    }
}
