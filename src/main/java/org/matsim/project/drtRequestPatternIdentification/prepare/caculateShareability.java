package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;
import java.util.Map;

public class caculateShareability {

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

        Population population = scenario.getPopulation();
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
        int numberOfDrtTrip = 0;

        List<TripStructureUtils.Trip> drtTripList = createTrips.drtTripList;

        //trip有问题

//        for (Person person : population.getPersons().values()) {
//            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
//
//        }

        //get drt trip set 1 必须要先建立drt的set 不可以是all set ，而且需要把list转为map。list只是一行数据，map是整个数据集
        //trip是使用某种交通工具的一段路，trips是一个人一趟旅程中（或者一个plan中）所有的交通方式合集

        for (TripStructureUtils.Trip trip1 : drtTripList) {
            numberOfShare++;

            //get drt trip set 2
            for (TripStructureUtils.Trip trip2 : drtTripList) {
                numberOfShare++;

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
                    System.out.println("未配对");
                    continue;
                }
                //o1,o2,d1,d2
                if (departureTime2 + maxWaitTime > departureTime1 + tripTimeO1ToO2) {
                    if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD1) {
                        if (departureTime2 + (alpha * tripTimeO2ToD2) + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD1 + tripTimeD1ToD2_1) {
                            numberOfShare ++;
                            System.out.println("已配对1");
                            continue;
                        }
                    }
                }
                //o1,o2,d2,d1
                if (departureTime2 + maxWaitTime > departureTime1 + tripTimeO1ToO2) {
                    if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD2) {
                        if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime1 + tripTimeO1ToO2 + tripTimeO2ToD2 + tripTimeD2ToD1_1) {
                            numberOfShare ++;
                            System.out.println("已配对2");
                            continue;
                        }
                    }
                    //o2,o1,d1,d2
                    if (departureTime1 + maxWaitTime > departureTime2 + tripTimeO2ToO1) {
                        if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD1) {
                            if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD1 + tripTimeD1ToD2_2) {
                                numberOfShare ++;
                                System.out.println("已配对3");
                                continue;
                            }
                        }
                    }
                    //o2,o1,d2,d1
                    if (departureTime1 + maxWaitTime > departureTime2 + tripTimeO2ToO1) {
                        if (departureTime2 + alpha * tripTimeO2ToD2 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2) {
                            if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + tripTimeO2ToO1 + tripTimeO1ToD2 + tripTimeD2ToD1_2) {
                                numberOfShare ++;
                                System.out.println("已配对4");
                            }
                        }
                    }
                }
            }
//            for (Person person : population.getPersons().values()) {
//                List<TripStructureUtils.Trip> trips_2 = TripStructureUtils.getTrips(person.getSelectedPlan());
//                numberOfDrtTrip++;
//                System.out.println("number of trips is: " + numberOfDrtTrip);
//            }
        }
        System.out.println("number of pairs is: " + numberOfShare);
    }
}

