package org.matsim.project.drtRequestPatternIdentification.prepare;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
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
//
//        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
//        Scenario scenario = ScenarioUtils.loadScenario(config);
//        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
//        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
//        Network network = scenario.getNetwork();
//        Population population = scenario.getPopulation();
//
//        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) drtPathZoneMap(network, population).get("tripPathZoneMap");
//    }

    private static final Logger log = LogManager.getLogger(DRTPathZoneSequence.class);

    public static Map<String, Object> drtPathZoneMap(Network network, Population population){

        // Create router (based on free speed)
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        // Get drt trip list
        log.info("---------------------------------------");
        log.info("drt trip list is started creating...");
        List<DrtDemand> drtDemandsSet = DrtDemandsList.getDrtDemandsList(network, population);
//        System.out.println("number of trips is: " + drtDemandsSet.size());
        log.info("drt trip list was successfully created");
        log.info("---------------------------------------");

        Map<Integer, List<Integer>> tripPathZoneMap = new HashMap<>();
        Map<Integer, DrtDemand> tripNumberMap = new HashMap<>();
        Map<List<Integer>, DrtDemand> demandPathMap = new HashMap<>();
        Map<Id<Link>,Integer> linkZoneMap = LinkZoneMap.linkZoneMap(network, population);//获取link对应的zone的map
        Map<String, Object> tripInfoMap = new HashMap<>();

        List<List<Integer>> listOfUniquePathZoneList = new ArrayList<>(); //保存所有demand行驶经过的zone列表，用于长短demand排序

        int tripNumber = 1;

        //遍历trip，创建path zone map
        log.info("---------------------------------------");
        log.info("path zone map is started creating...");
        for (DrtDemand demand: drtDemandsSet) {

            List<Integer> pathZoneList = new ArrayList<>();//该trip经过路径上的zone list(zone有重复）
            List<Integer> uniquePathZoneList = new ArrayList<>();;//该trip无重复的zone list

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
                pathZoneList.add(zoneId);//该trip的路径集合，将经过的zone按顺序添加进zoneList

                //创建一个无重复项的zone list（将上面得到的list进行去重）
                Set<Integer> uniquePathZoneSet = new LinkedHashSet<>(pathZoneList); //将原来有重复项的list，转化为无重复项且保留顺序的set
                uniquePathZoneList = new ArrayList<>(uniquePathZoneSet);
            }
            demandPathMap.put(uniquePathZoneList, demand);//将该demand和其路径作为键值对加入map，方便后续根据demand查找路径list
            listOfUniquePathZoneList.add(uniquePathZoneList);//将该demand的路径，list加入收集所有路径的，方便后续排序
        }

        listOfUniquePathZoneList.sort((list1, list2) -> list2.size() - list1.size());//将demand按照从长到短重新排序

        for(List<Integer> sortedPathZoneList: listOfUniquePathZoneList){

            DrtDemand demand = demandPathMap.get(sortedPathZoneList);//根据路径list，得到其对应的demand

            tripNumberMap.put(tripNumber, demand);//将trip序号与该demand作为键值对，存进map
            tripPathZoneMap.put(tripNumber,sortedPathZoneList);//将trip序号与该路径的zone list作为键值对，存进map

            tripNumber++;
        }
//        System.out.println(listOfUniquePathZoneList.size());
        log.info("path zone map was successfully created");
        log.info("---------------------------------------");


        tripInfoMap.put("tripPathZoneMap", tripPathZoneMap);
        tripInfoMap.put("tripNumberMap", tripNumberMap);
        return tripInfoMap;
    }
}

