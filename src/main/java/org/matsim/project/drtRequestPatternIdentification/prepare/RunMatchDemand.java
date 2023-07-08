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
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.utils.LinkToLinkTravelTimeMatrix;
import scala.Int;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RunMatchDemand {
    public static void main(String[] args) {

        // Create scenario based on config file
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }
        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());

        matchDemand(config);
    }

    public static void matchDemand(Config config){

        Scenario scenario = ScenarioUtils.loadScenario(config);
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
        Network network = scenario.getNetwork();

        Map<String, Object> tripInfoMap = DRTPathZoneSequence.DRTPathZoneMap(config);//得到trip的两个map
        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) tripInfoMap.get("tripPathZoneMap");//得到trip和其经过path的map
        Map<Integer, TripStructureUtils.Trip> tripNumberMap = (Map<Integer, TripStructureUtils.Trip>) tripInfoMap.get("tripNumberMap");

        // Create router (based on free speed)
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;

        // Get drt trip set
        List<TripStructureUtils.Trip> drtTripSet = DrtTripsSet.getDrtTripSet(config);

        int numberOfMatch = 0;

        for (int trip1Number = 1; trip1Number <= tripPathZoneMap.size(); trip1Number++){
            List<Integer> trip1PathZoneList = tripPathZoneMap.get(trip1Number);
            TripStructureUtils.Trip trip1 = tripNumberMap.get(trip1Number);//get trip 1
            for (int trip2Number = 1; trip2Number <= tripPathZoneMap.size(); trip2Number++){
                List<Integer> trip2PathZoneList = tripPathZoneMap.get(trip2Number);
                TripStructureUtils.Trip trip2 = tripNumberMap.get(trip2Number);//get trip 2
                if(trip1PathZoneList == trip2PathZoneList){
                    continue;
                }
                if(Collections.indexOfSubList(trip1PathZoneList, trip2PathZoneList) != -1){//如果trip1的行程包含trip2的行程 -> 空间上两个trip可以match

                    Link fromLink1 = network.getLinks().get(trip1.getOriginActivity().getLinkId());
                    Link toLink1 = network.getLinks().get(trip1.getDestinationActivity().getLinkId());
                    Link fromLink2 = network.getLinks().get(trip2.getOriginActivity().getLinkId());
                    Link toLink2 = network.getLinks().get(trip2.getDestinationActivity().getLinkId());

                    double departureTime1 = trip1.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
                    double departureTime2 = trip2.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
                    // o1 to o2
                    VrpPathWithTravelData pathO1ToO2 = VrpPaths.calcAndCreatePath(fromLink1, fromLink2, 0, router, travelTime);
                    double tripTimeO1ToO2 = pathO1ToO2.getTravelTime();
                    // d2 to d1
                    VrpPathWithTravelData pathD2ToD1 = VrpPaths.calcAndCreatePath(toLink2, toLink1, 0, router, travelTime);
                    double tripTimeD2ToD1 = pathD2ToD1.getTravelTime();
                    // o1 to d1
                    VrpPathWithTravelData pathO1ToD1 = VrpPaths.calcAndCreatePath(fromLink1, toLink1, 0, router, travelTime);
                    double tripTimeO1ToD1 = pathO1ToD1.getTravelTime();
                    // o2 to d2
                    VrpPathWithTravelData pathO2ToD2 = VrpPaths.calcAndCreatePath(fromLink2, toLink2, 0, router, travelTime);
                    double tripTimeO2ToD2 = pathO2ToD2.getTravelTime();

                    //判断这两个trip在时间上是否match （O1到O2的时间，D2到D1的时间）
                    //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                    if (departureTime2 + maxWaitTime > departureTime1 + tripTimeO1ToO2) {
                        //D1的最晚到达时间 > D2最晚到达时间＋D2到D1的时间
                        if (departureTime1 + alpha * tripTimeO1ToD1 + beta > departureTime2 + alpha * tripTimeO2ToD2 + beta+tripTimeD2ToD1) {
                            numberOfMatch++;
                        }
                    }
                }
            }
        }

        int numberOfTotalPairs = (drtTripSet.size() * drtTripSet.size()- 1);
        System.out.println("number of total pairs is: " + numberOfTotalPairs);
        System.out.println("number of match is: " + numberOfMatch);
    }
}
