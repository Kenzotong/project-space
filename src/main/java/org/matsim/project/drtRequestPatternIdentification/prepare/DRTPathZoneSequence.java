package org.matsim.project.drtRequestPatternIdentification.prepare;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
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

import java.util.*;

public class DRTPathZoneSequence {

    public static void main(String[] args) {

        // Create scenario based on config file
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }
        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());

        DRTPathLinkMap(config);
    }

    public static void DRTPathLinkMap(Config config){

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();

        // Create router (based on free speed)
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        // Get drt trip set
        List<TripStructureUtils.Trip> drtTripSet = DrtTripsSet.getDrtTripSet(config);
        System.out.println("number of trips is: " + drtTripSet.size());

        Map<Integer, List<Integer>> tripPathZoneMap = new HashMap<>();
        Map<Id<Link>,Integer> linkZoneMap = LinkZoneMap.linkZoneMap(config);//获取link对应的zone的map

        int tripNumber = 1;

        //遍历trip，创建path zone map
        for (TripStructureUtils.Trip trip : drtTripSet) {

            List<Integer> pathZoneList = new ArrayList<>();//每个trip有单独的zone list(zone有重复）
            List<Integer> uniquePathZoneList = new ArrayList<>();//无重复的zone list

            Link fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
            Link toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());

            // Get path link list from each trip
            LeastCostPathCalculator.Path path = router.calcLeastCostPath(fromLink.getToNode(), toLink.getFromNode(),
                    0, null, null);
            List<Link> pathLinkList = path.links;//当前trip行驶路径经过的所有link

            //遍历这个当前link list，为每一个link找到对应的zone，并创建该路径经过的zone的list
            for(Link link : pathLinkList){
                Id<Link> linkId = link.getId();
                int zoneId = linkZoneMap.get(linkId);
                pathZoneList.add(zoneId);//将经过的zone按顺序添加进zoneList

                //创建一个无重复项的zone list（将上面得到的list进行去重）
                Set<Integer> uniquePathZoneSet = new LinkedHashSet<>(pathZoneList); //将原来有重复项的list，转化为无重复项且保留顺序的set
                uniquePathZoneList = new ArrayList<>(uniquePathZoneSet);

            }

            tripPathZoneMap.put(tripNumber,uniquePathZoneList);//将trip序号与该路径的zone list相连接，得到每一个trip的路径区域序列

            tripNumber++;
        }

        System.out.println(tripPathZoneMap);
    }
}

