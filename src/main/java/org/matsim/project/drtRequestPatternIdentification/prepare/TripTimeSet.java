package org.matsim.project.drtRequestPatternIdentification.prepare;
//
//import org.matsim.api.core.v01.Scenario;
//import org.matsim.api.core.v01.network.Link;
//import org.matsim.api.core.v01.network.Network;
////import org.matsim.contrib.drt.run.DrtConfigGroup;
//import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
//import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
//import org.matsim.contrib.dvrp.path.VrpPaths;
//import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
//import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
//import org.matsim.core.config.Config;
//import org.matsim.core.config.ConfigUtils;
//import org.matsim.core.network.NetworkUtils;
//import org.matsim.core.router.TripStructureUtils;
//import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
//import org.matsim.core.router.speedy.SpeedyALTFactory;
//import org.matsim.core.router.util.LeastCostPathCalculator;
//import org.matsim.core.router.util.TravelDisutility;
//import org.matsim.core.router.util.TravelTime;
//import org.matsim.core.scenario.ScenarioUtils;
//
//import java.util.*;
//
@Deprecated
public class TripTimeSet {
////    public static void main(String[] args){
////        System.out.println(getTripTimeSet(config).size());
////    }
//
//    public static Map<Link, Map<Link, Double>> getTripTimeSet(Config config) {
//        // Create scenario based on config file
////        String configPath = "D:\\Thesis\\mielec\\mielec-scenario\\mielec_drt_config.xml";
//
////        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
//        Scenario scenario = ScenarioUtils.loadScenario(config);
//        Network network = scenario.getNetwork();
//
//        // Create router (based on free speed)
//        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
//        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
//        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);
//
//        // Get drt trip set
//        List<TripStructureUtils.Trip> drtTripSet = DrtTripsSet.getDrtTripSet(config);
//        System.out.println(drtTripSet.size());
//
//        // Create maps to store travel time
//        Map<Link, Map<Link, Double>> tripTimeOuterMap = new HashMap<>();
//
//
//        // Get drt trip set 1
//        for (TripStructureUtils.Trip trip1 : drtTripSet) {
//
//            // get OD link
//            // When link id is written in the plan
//            Link fromLink1 = network.getLinks().get(trip1.getOriginActivity().getLinkId());
//            Link toLink1 = network.getLinks().get(trip1.getDestinationActivity().getLinkId());
//
//            // When link id is not provided (coordinate is provided instead)
////            Link fromLink1 = NetworkUtils.getNearestLink(network, trip1.getOriginActivity().getCoord());
////            Link toLink1 = NetworkUtils.getNearestLink(network, trip1.getDestinationActivity().getCoord());
//
//            //Get drt trip set 2
//            for (TripStructureUtils.Trip trip2 : drtTripSet) {
//
//                if (trip1 == trip2) {
//                    continue;
//                }
//
//                // get OD link
//                // When link id is written in the plan
//                Link fromLink2 = network.getLinks().get(trip2.getOriginActivity().getLinkId());
//                Link toLink2 = network.getLinks().get(trip2.getDestinationActivity().getLinkId());
//
//                // When link id is not provided (coordinate is provided instead)
////                Link fromLink2 = NetworkUtils.getNearestLink(network, trip2.getOriginActivity().getCoord());
////                Link toLink2 = NetworkUtils.getNearestLink(network, trip2.getDestinationActivity().getCoord());
//
//                // Calculate travel time
//                // o1 to o2
//                VrpPathWithTravelData pathO1ToO2 = VrpPaths.calcAndCreatePath(fromLink1, fromLink2, 0, router, travelTime);
//                double tripTimeO1ToO2 = pathO1ToO2.getTravelTime();
//                // Determine if outer map is existed
//                if (tripTimeOuterMap.containsKey(fromLink1)) {
//                    tripTimeOuterMap.get(fromLink1).put(fromLink2, tripTimeO1ToO2);
//                } else {
//                    Map<Link, Double> tripTimeInnerMap = new HashMap<>();
//                    tripTimeInnerMap.put(fromLink2, tripTimeO1ToO2);
//                    tripTimeOuterMap.put(fromLink1, tripTimeInnerMap);
//                }
//
//                // o2 to o1
//                VrpPathWithTravelData pathO2ToO1 = VrpPaths.calcAndCreatePath(fromLink2, fromLink1, 0, router, travelTime);
//                double tripTimeO2ToO1 = pathO2ToO1.getTravelTime();
//                // Determine if outer map is existed
//                if (tripTimeOuterMap.containsKey(fromLink2)) {
//                    tripTimeOuterMap.get(fromLink2).put(fromLink1, tripTimeO2ToO1);
//                } else {
//                    Map<Link, Double> tripTimeInnerMap = new HashMap<>();
//                    tripTimeInnerMap.put(fromLink1, tripTimeO2ToO1);
//                    tripTimeOuterMap.put(fromLink2, tripTimeInnerMap);
//                }
//
//                // o2 to d1
//                VrpPathWithTravelData pathO2ToD1 = VrpPaths.calcAndCreatePath(fromLink2, toLink1, 0, router, travelTime);
//                double tripTimeO2ToD1 = pathO2ToD1.getTravelTime();
//                // Determine if outer map is existed
//                if (tripTimeOuterMap.containsKey(fromLink2)) {
//                    tripTimeOuterMap.get(fromLink2).put(toLink1, tripTimeO2ToD1);
//                } else {
//                    Map<Link, Double> tripTimeInnerMap = new HashMap<>();
//                    tripTimeInnerMap.put(toLink1, tripTimeO2ToD1);
//                    tripTimeOuterMap.put(fromLink2, tripTimeInnerMap);
//                }
//
//                // o2 to d2
//                VrpPathWithTravelData pathO2ToD2 = VrpPaths.calcAndCreatePath(fromLink2, toLink2, 0, router, travelTime);
//                double tripTimeO2ToD2 = pathO2ToD2.getTravelTime();
//                // Determine if outer map is existed
//                if (tripTimeOuterMap.containsKey(fromLink2)) {
//                    tripTimeOuterMap.get(fromLink2).put(toLink2, tripTimeO2ToD2);
//                } else {
//                    Map<Link, Double> tripTimeInnerMap = new HashMap<>();
//                    tripTimeInnerMap.put(toLink2, tripTimeO2ToD2);
//                    tripTimeOuterMap.put(fromLink2, tripTimeInnerMap);
//                }
//
//                // o1 to d1
//                VrpPathWithTravelData pathO1ToD1 = VrpPaths.calcAndCreatePath(fromLink1, toLink1, 0, router, travelTime);
//                double tripTimeO1ToD1 = pathO1ToD1.getTravelTime();
//                // Determine if outer map is existed
//                if (tripTimeOuterMap.containsKey(fromLink1)) {
//                    tripTimeOuterMap.get(fromLink1).put(toLink1, tripTimeO1ToD1);
//                } else {
//                    Map<Link, Double> tripTimeInnerMap = new HashMap<>();
//                    tripTimeInnerMap.put(toLink1, tripTimeO1ToD1);
//                    tripTimeOuterMap.put(fromLink1, tripTimeInnerMap);
//                }
//
//                // o1 to d2
//                VrpPathWithTravelData pathO1ToD2 = VrpPaths.calcAndCreatePath(fromLink1, toLink2, 0, router, travelTime);
//                double tripTimeO1ToD2 = pathO1ToD2.getTravelTime();
//                // Determine if outer map is existed
//                if (tripTimeOuterMap.containsKey(fromLink1)) {
//                    tripTimeOuterMap.get(fromLink1).put(toLink2, tripTimeO1ToD2);
//                } else {
//                    Map<Link, Double> tripTimeInnerMap = new HashMap<>();
//                    tripTimeInnerMap.put(toLink2, tripTimeO1ToD2);
//                    tripTimeOuterMap.put(fromLink1, tripTimeInnerMap);
//                }
//
//                // d1 to d2
//                VrpPathWithTravelData pathD1ToD2 = VrpPaths.calcAndCreatePath(toLink1, toLink2, 0, router, travelTime);
//                double tripTimeD1ToD2 = pathD1ToD2.getTravelTime();
//                // Determine if outer map is existed
//                if (tripTimeOuterMap.containsKey(toLink1)) {
//                    tripTimeOuterMap.get(toLink1).put(toLink2, tripTimeD1ToD2);
//                } else {
//                    Map<Link, Double> tripTimeInnerMap = new HashMap<>();
//                    tripTimeInnerMap.put(toLink2, tripTimeD1ToD2);
//                    tripTimeOuterMap.put(toLink1, tripTimeInnerMap);
//                }
//
//                // d2 to d1
//                VrpPathWithTravelData pathD2ToD1 = VrpPaths.calcAndCreatePath(toLink2, toLink1, 0, router, travelTime);
//                double tripTimeD2ToD1 = pathD2ToD1.getTravelTime();
//                // Determine if outer map is existed
//                if (tripTimeOuterMap.containsKey(toLink2)) {
//                    tripTimeOuterMap.get(toLink2).put(toLink1, tripTimeD2ToD1);
//                } else {
//                    Map<Link, Double> tripTimeInnerMap = new HashMap<>();
//                    tripTimeInnerMap.put(toLink1, tripTimeD2ToD1);
//                    tripTimeOuterMap.put(toLink2, tripTimeInnerMap);
//                }
//            }
//        }
//        return tripTimeOuterMap;
//    }
}