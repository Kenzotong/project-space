package org.matsim.project.drtRequestPatternIdentification.prepare;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
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
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;

import java.util.*;

public class DRTPathZoneSequence {


//    public static void main(String[] args) {
//
//        // Create scenario based on config file
//        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
//        if (args.length != 0) {
//            configPath = args[0];
//        }
//        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
//
//        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) DRTPathZoneMap(config).get("tripPathZoneMap");
//        Map<Integer, DrtDemand> tripNumberMap = (Map<Integer, DrtDemand>) DRTPathZoneMap(config).get("tripNumberMap");
//        System.out.println(tripNumberMap);
//    }

    public static Map<String, Object> drtPathZoneMap(Network network, Population population){

        // Create router (based on free speed)
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        // Get drt trip set
        List<DrtDemand> drtDemandsSet = DrtDemandsSet.getDrtDemandsSet(network, population);
//        System.out.println("number of trips is: " + drtDemandsSet.size());

        Map<Integer, List<Integer>> tripPathZoneMap = new HashMap<>();
        Map<Integer, DrtDemand> tripNumberMap = new HashMap<>();
        Map<Id<Link>,Integer> linkZoneMap = LinkZoneMap.linkZoneMap(network);//获取link对应的zone的map
        Map<String, Object> tripInfoMap = new HashMap<>();

        int tripNumber = 1;

        //遍历trip，创建path zone map
        for (DrtDemand demand: drtDemandsSet) {

            tripNumberMap.put(tripNumber, demand);//以便用trip number查找对应的trip

            List<Integer> pathZoneList = new ArrayList<>();//每个trip有单独的zone list(zone有重复）
            List<Integer> uniquePathZoneList = new ArrayList<>();//无重复的zone list

            Link fromLink = demand.fromLink();
            Link toLink = demand.toLink();

            // 计算当前trip的路径，并收集路径经过的link
            LeastCostPathCalculator.Path path = router.calcLeastCostPath(fromLink.getToNode(), toLink.getFromNode(),
                    0, null, null);
            List<Link> pathLinkList = path.links;

            //遍历这个当前link list，为每一个link找到对应的zone，并创建该路径经过的zone的list
            for(Link link : pathLinkList){
                Id<Link> linkId = link.getId();
                int zoneId = linkZoneMap.get(linkId);//得到zone id
                pathZoneList.add(zoneId);//将经过的zone按顺序添加进zoneList

                //创建一个无重复项的zone list（将上面得到的list进行去重）
                Set<Integer> uniquePathZoneSet = new LinkedHashSet<>(pathZoneList); //将原来有重复项的list，转化为无重复项且保留顺序的set
                uniquePathZoneList = new ArrayList<>(uniquePathZoneSet);

            }

            tripPathZoneMap.put(tripNumber,uniquePathZoneList);//将trip序号与该路径的zone list相连接，得到每一个trip的路径区域序列

            tripNumber++;
        }

        tripInfoMap.put("tripPathZoneMap", tripPathZoneMap);
        tripInfoMap.put("tripNumberMap", tripNumberMap);
        return tripInfoMap;
    }
}

