package org.matsim.project.drtRequestPatternIdentification.prepare.algorithm2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.Tools;
import org.matsim.project.drtRequestPatternIdentification.prepare.DrtDemandsList;
import org.matsim.project.drtRequestPatternIdentification.prepare.RunDemandWithTTM;
import org.matsim.project.utils.LinkToLinkTravelTimeMatrix;

import java.util.*;

public class RunDemandQuantificationK4 {
    private static final Logger log = LogManager.getLogger(RunDemandWithTTM.class);

    public static void main(String[] args) {

        // Create scenario based on config file
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\kelheim\\kelheim_drt_config.xml.xml";
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
        Population population = scenario.getPopulation();

        log.info("trip information map is started creating...");
        Map<String, Object> tripInfoMap = DRTPathZoneSequence.drtPathZoneMap(network, population);//得到trip的两个map
        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) tripInfoMap.get("tripPathZoneMap");//得到trip和其经过path的map
//        log.info("there is " + tripPathZoneMap.size() + " trips");
        Map<Integer, DrtDemand> tripNumberMap = (Map<Integer, DrtDemand>) tripInfoMap.get("tripNumberMap");
//        log.info("there is " + tripNumberMap.size() + " trips");

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;

        // Get drt demands
        List<DrtDemand> drtDemands = DrtDemandsList.getDrtDemandsList(network, population);

        // initialize travelTimeMatrix (based on free speed)
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        Set<Id<Link>> relevantLinks = Tools.collectRelevantLink(drtDemands);//收集所有trip的起终link Id
        LinkToLinkTravelTimeMatrix travelTimeMatrix = new LinkToLinkTravelTimeMatrix(network, travelTime, relevantLinks, 0);

        double numOfMatchK42 = 0;
        double numOfMatchK43 = 0;
        double numOfMatchK44 = 0;
        double totalTravelTime = 0;
        double savingTravelTimeK42 = 0;
        double savingTravelTimeK43 = 0;
        double savingTravelTimeK44 = 0;


        //(K=4) 空间上和时间上判断4个以内的行程是否match
        log.info("---------------------------------------------------------------------------------------------------------------------");
        log.info("start matching (shared by 2 trips AND 3 trips AND 4 trips)...");
        log.info("shareable trip pairs map is started creating (shared by 2 trips)...");

        //收集所有可共享的两两配对
        Map<Integer,List<Integer>> shareableTwoTripMap = new LinkedHashMap<>();//key为supplier，value为demander,有顺序的
        for (int i = 1; i <= tripPathZoneMap.size(); i++){
            List<Integer> shareableTrips = new ArrayList<>();
            List<Integer> supplierPathZoneList = tripPathZoneMap.get(i);
            DrtDemand supplier = tripNumberMap.get(i);//get demand 1 （supplier）

            double supplierDirectTravelTime = travelTimeMatrix.getTravelTime(supplier.fromLink(), supplier.toLink(), supplier.departureTime());
            double supplierLatestDepartureTime = supplier.departureTime() + maxWaitTime;
            double supplierLatestArrivalTime = supplier.departureTime() + alpha * supplierDirectTravelTime + beta;

            totalTravelTime += supplierDirectTravelTime;

            for (int j = i + 1; j <= tripPathZoneMap.size(); j++){
                List<Integer> demanderPathZoneList = tripPathZoneMap.get(j);
                DrtDemand demander = tripNumberMap.get(j);//get demand2 （demander）

                if(Collections.indexOfSubList(supplierPathZoneList, demanderPathZoneList) != -1){//判断supplier的行程是否包含demander的行程 -> 空间上两个demand可以match -> 进而再从时间上判断

                    double demanderDirectTravelTime = travelTimeMatrix.getTravelTime(demander.fromLink(), demander.toLink(), demander.departureTime());
                    double demanderLatestDepartureTime = demander.departureTime() + maxWaitTime;
                    double demanderLatestArrivalTime = demander.departureTime() + alpha * demanderDirectTravelTime + beta;

                    //判断这两个trip在时间上是否match （o1,o2,d2,d1）
                    //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                    double now = supplier.departureTime() + stopDuration;//supplier是o1和d1，demander是o2和d2
                    double o1o2 = travelTimeMatrix.getTravelTime(supplier.fromLink(), demander.fromLink(), now);
                    double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                    //demander出发时间 <= demander最晚出发时间
                    if (arrivalTimeO2 <= demanderLatestDepartureTime) {
                        now = arrivalTimeO2 + stopDuration;
                        double arrivalTimeD2 = now + demanderDirectTravelTime;
                        //o2到d2的时间 <= demander的最晚到达
                        if (arrivalTimeD2 <= demanderLatestArrivalTime) {
                            now = arrivalTimeD2 + stopDuration;
                            double d2d1 = travelTimeMatrix.getTravelTime(demander.toLink(), supplier.toLink(), now);
                            double arrivalTimeD1 = now + d2d1;
                            //d2到d1的时间 <= d1的最晚到达
                            if (arrivalTimeD1 <= supplierLatestArrivalTime){
                                shareableTrips.add(j);//将可以匹配的demander加入 shared trips中
                            }
                        }
                    }
                }
            }
            if (!shareableTrips.isEmpty()) {
                shareableTwoTripMap.put(i, shareableTrips);//获得map：supplier作为key，所有可以和它匹配的demander为value
            }
//            log.info("shareable trip pairs map was: " + shareableTripPairsMap);
        }

        log.info("shareable trip pairs map was successfully created (shared by 2 trips)");
        log.info("---------------------------------------------------------------------------------------------------------------------");
        log.info("shareable trip pairs map is started creating (shared by 3 trips)...");

        //收集所有可共享的三个之间的配对
        Map<Integer, Map<Integer, List<Integer>>> shareableThreeTripsMap = new LinkedHashMap<>();
        for (int x = 1; x <= tripPathZoneMap.size(); x++){
            Map<Integer, List<Integer>> subShareableTripsMap = new LinkedHashMap<>();
            List<Integer> mainSupplierPathZoneList = tripPathZoneMap.get(x);
            DrtDemand mainSupplier = tripNumberMap.get(x);//get demand 1 （mainSupplier）

            for (int y = x + 1; y <= tripPathZoneMap.size(); y++) {
                List<Integer> shareableTrips = new ArrayList<>();
                List<Integer> subSupplierPathZoneList = tripPathZoneMap.get(y);
                DrtDemand subSupplier = tripNumberMap.get(y);//get demand 2 （subSupplier）

                if (Collections.indexOfSubList(mainSupplierPathZoneList, subSupplierPathZoneList) != -1) {//判断mainSupplier的行程是否包含subSupplier的行程 -> 空间上两个demand可以match

                    for (int z = y + 1; z <= tripPathZoneMap.size(); z++) {
                        List<Integer> demanderPathZoneList = tripPathZoneMap.get(z);
                        DrtDemand demander = tripNumberMap.get(z);//get demand 3 （demander）

                        if (Collections.indexOfSubList(subSupplierPathZoneList, demanderPathZoneList) != -1) {//判断subSupplier的行程是否包含demander的行程 -> 空间上两个demand可以match

                            double mainSupplierDirectTravelTime = travelTimeMatrix.getTravelTime(mainSupplier.fromLink(), mainSupplier.toLink(), mainSupplier.departureTime());
                            double mainSupplierLatestDepartureTime = mainSupplier.departureTime() + maxWaitTime;
                            double mainSupplierLatestArrivalTime = mainSupplier.departureTime() + alpha * mainSupplierDirectTravelTime + beta;

                            double subSupplierDirectTravelTime = travelTimeMatrix.getTravelTime(subSupplier.fromLink(), subSupplier.toLink(), subSupplier.departureTime());
                            double subSupplierLatestDepartureTime = subSupplier.departureTime() + maxWaitTime;
                            double subSupplierLatestArrivalTime = subSupplier.departureTime() + alpha * subSupplierDirectTravelTime + beta;

                            double demanderDirectTravelTime = travelTimeMatrix.getTravelTime(demander.fromLink(), demander.toLink(), demander.departureTime());
                            double demanderLatestDepartureTime = demander.departureTime() + maxWaitTime;
                            double demanderLatestArrivalTime = demander.departureTime() + alpha * demanderDirectTravelTime + beta;

                            //判断三个行程之间在时间上是否匹配
                            //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                            double now = mainSupplier.departureTime() + stopDuration;//main supplier是o1和d1，sub supplier是o2和d2，demander是o3和d3
                            double o1o2 = travelTimeMatrix.getTravelTime(mainSupplier.fromLink(), subSupplier.fromLink(), now);
                            double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                            //o2的到达时间 <= o2最晚出发时间
                            if (arrivalTimeO2 <= subSupplierLatestDepartureTime) {
                                now = arrivalTimeO2 + stopDuration;
                                double o2o3 = travelTimeMatrix.getTravelTime(subSupplier.fromLink(), demander.fromLink(), now);
                                double arrivalTimeO3 = now + o2o3;
                                //o3的到达时间 <= o3最晚出发时间
                                if (arrivalTimeO3 <= demanderLatestDepartureTime) {
                                    now = arrivalTimeO3 + stopDuration;
                                    double arrivalTimeD3 = now + demanderDirectTravelTime;
                                    //o3到d3的时间 <= demander的最晚到达
                                    if (arrivalTimeD3 <= demanderLatestArrivalTime) {
                                        now = arrivalTimeD3 + stopDuration;
                                        double d3d2 = travelTimeMatrix.getTravelTime(demander.toLink(), subSupplier.toLink(), now);
                                        double arrivalTimeD2 = now + d3d2;
                                        //d2的到达时间 <= sub supplier的最晚达到
                                        if (arrivalTimeD2 <= subSupplierLatestArrivalTime) {
                                            now = arrivalTimeD2 + stopDuration;
                                            double d2d1 = travelTimeMatrix.getTravelTime(subSupplier.toLink(), mainSupplier.toLink(), now);
                                            double arrivalTimeD1 = now + d2d1;
                                            //d1的到达时间 <= d1最晚到达
                                            if (arrivalTimeD1 <= mainSupplierLatestArrivalTime) {
                                               shareableTrips.add(z);//把可以匹配的demander加入列表
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                    if(!shareableTrips.isEmpty()){
                        subShareableTripsMap.put(y,shareableTrips);
                    }
                }
            }
            if(!subShareableTripsMap.isEmpty()){
                shareableThreeTripsMap.put(x,subShareableTripsMap);
            }
        }

        log.info("shareable trip pairs map was successfully created (shared by 3 trips)");
        log.info("---------------------------------------------------------------------------------------------------------------------");
        log.info("start matching (shared by 4 trips)...");

        //TODO 进行k=4的配对（利用已知的K=3的配对组合来筛选）
        //判断空间上4个demand是否匹配
        HashSet<Integer> pooledDemandK44 = new HashSet<>();//收集已经匹配的demand
        for (int a : shareableThreeTripsMap.keySet()){
            //判断是否匹配过了
            if (pooledDemandK44.contains(a)){
                continue;
            }

            outerLoop: for(int b : shareableThreeTripsMap.get(a).keySet()){
                //判断是否匹配过了
                if (pooledDemandK44.contains(b)){
                    continue;
                }
                if (shareableThreeTripsMap.containsKey(b)) {

                    for (int i = 0; i <= shareableThreeTripsMap.get(a).get(b).size() - 1; i++) {
                        int c = shareableThreeTripsMap.get(a).get(b).get(i);
                        //判断是否匹配过了
                        if (pooledDemandK44.contains(c)) {
                            continue;
                        }
                        if(shareableThreeTripsMap.get(b).containsKey(c)){
                            for(int j = 0; j <= shareableThreeTripsMap.get(b).get(c).size() - 1; j++){
                                int d = shareableThreeTripsMap.get(b).get(c).get(j);
                                //判断是否匹配过了
                                if (pooledDemandK44.contains(d)){
                                    continue;
                                }
                                if (shareableThreeTripsMap.get(a).get(b).contains(d)){
                                    //判断这4个trip在时间上是否match （顺序为o1,o2,o3,o4,d4,d3,d2,d1）
                                    DrtDemand demand1 = tripNumberMap.get(a);//得到第1长的demand
                                    double demand1DirectTravelTime = travelTimeMatrix.getTravelTime(demand1.fromLink(), demand1.toLink(), demand1.departureTime());
                                    double demand1LatestDepartureTime = demand1.departureTime() + maxWaitTime;
                                    double demand1LatestArrivalTime = demand1.departureTime() + alpha * demand1DirectTravelTime + beta;

                                    DrtDemand demand2 = tripNumberMap.get(b);//得到第2长的demand
                                    double demand2DirectTravelTime = travelTimeMatrix.getTravelTime(demand2.fromLink(), demand2.toLink(), demand2.departureTime());
                                    double demand2LatestDepartureTime = demand2.departureTime() + maxWaitTime;
                                    double demand2LatestArrivalTime = demand2.departureTime() + alpha * demand2DirectTravelTime + beta;

                                    DrtDemand demand3 = tripNumberMap.get(c);//得到第3长的demand
                                    double demand3DirectTravelTime = travelTimeMatrix.getTravelTime(demand3.fromLink(), demand3.toLink(), demand3.departureTime());
                                    double demand3LatestDepartureTime = demand3.departureTime() + maxWaitTime;
                                    double demand3LatestArrivalTime = demand3.departureTime() + alpha * demand3DirectTravelTime + beta;

                                    DrtDemand demand4 = tripNumberMap.get(d);//得到最短的demand
                                    double demand4DirectTravelTime = travelTimeMatrix.getTravelTime(demand4.fromLink(), demand4.toLink(), demand4.departureTime());
                                    double demand4LatestDepartureTime = demand4.departureTime() + maxWaitTime;
                                    double demand4LatestArrivalTime = demand4.departureTime() + alpha * demand4DirectTravelTime + beta;

                                    //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                                    double now = demand1.departureTime() + stopDuration;
                                    double o1o2 = travelTimeMatrix.getTravelTime(demand1.fromLink(), demand2.fromLink(), now);
                                    double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                                    //o2的到达时间 <= o2最晚出发时间
                                    if (arrivalTimeO2 <= demand2LatestDepartureTime) {
                                        now = arrivalTimeO2 + stopDuration;
                                        double o2o3 = travelTimeMatrix.getTravelTime(demand2.fromLink(), demand3.fromLink(), now);
                                        double arrivalTimeO3 = now + o2o3;
                                        //o3的到达时间 <= o3最晚出发时间
                                        if (arrivalTimeO3 <= demand3LatestDepartureTime) {
                                            now = arrivalTimeO3 + stopDuration;
                                            double o3o4 = travelTimeMatrix.getTravelTime(demand3.fromLink(), demand4.fromLink(), now);
                                            double arrivalTimeO4 = now + o3o4;
                                            //o4的到达时间 <= o4最晚出发时间
                                            if (arrivalTimeO4 <= demand4LatestDepartureTime) {
                                                now = arrivalTimeO4 + stopDuration;
                                                double arrivalTimeD4 = now + demand4DirectTravelTime;
                                                //o4到d4的时间 <= demand4的最晚到达
                                                if (arrivalTimeD4 <= demand4LatestArrivalTime) {
                                                    now = arrivalTimeD4 + stopDuration;
                                                    double d4d3 = travelTimeMatrix.getTravelTime(demand4.toLink(), demand3.toLink(), now);
                                                    double arrivalTimeD3 = now + d4d3;
                                                    //d4到d3的时间 <= demand3的最晚到达
                                                    if (arrivalTimeD3 <= demand3LatestArrivalTime) {
                                                        now = arrivalTimeD3 + stopDuration;
                                                        double d3d2 = travelTimeMatrix.getTravelTime(demand3.toLink(), demand2.toLink(), now);
                                                        double arrivalTimeD2 = now + d3d2;
                                                        //d3到d2的时间 <= demand2的最晚到达
                                                        if (arrivalTimeD2 <= demand2LatestArrivalTime) {
                                                            now = arrivalTimeD2 + stopDuration;
                                                            double d2d1 = travelTimeMatrix.getTravelTime(demand2.toLink(), demand1.toLink(), now);
                                                            double arrivalTimeD1 = now + d2d1;
                                                            //d2到d1的时间 <= d1最晚到达
                                                            if (arrivalTimeD1 <= demand1LatestArrivalTime) {
                                                                numOfMatchK44++;
                                                                pooledDemandK44.add(a);
                                                                pooledDemandK44.add(b);
                                                                pooledDemandK44.add(c);
                                                                pooledDemandK44.add(d);
                                                                savingTravelTimeK44 += demand1DirectTravelTime + demand2DirectTravelTime + demand3DirectTravelTime + demand4DirectTravelTime - (arrivalTimeD1 - demand1.departureTime()); //4段各自direct的时间之和 - (d1的实际到达时间 - o1的出发时间)
                                                                break outerLoop;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        log.info("matching (shared by 4 trips) was over");
        log.info("---------------------------------------------------------------------------------------------------------------------");
        log.info("start matching (shared by 3 trips)...");

        //判断剩下的demands在空间上3个demand是否匹配
        HashSet<Integer> pooledDemandK43 = new HashSet<>();//收集已经匹配的demand
        for (int x : shareableTwoTripMap.keySet()){
            //判断是否已经匹配过了
            if (pooledDemandK44.contains(x) || pooledDemandK43.contains(x)){
                continue;
            }

            outerLoop: for (int i = 0; i <= shareableTwoTripMap.get(x).size() - 1; i++) {
                int y = shareableTwoTripMap.get(x).get(i);
                //判断是否已经匹配过了
                if (pooledDemandK44.contains(y) || pooledDemandK43.contains(y)){
                    continue;
                }

                if (shareableTwoTripMap.containsKey(y)) {
                    for (int j = 0; j <= shareableTwoTripMap.get(y).size() - 1; j++) {
                        int z = shareableTwoTripMap.get(y).get(j);
                        //判断是否已经匹配过了
                        if (pooledDemandK44.contains(z) || pooledDemandK43.contains(z)){
                            continue;
                        }

                        if (shareableTwoTripMap.get(x).contains(z)) {
                            //判断这三个trip在时间上是否match （o1,o2,o3,d3,d2,d1）
                            DrtDemand mainSupplier = tripNumberMap.get(x);//得到最长的demand
                            double mainSupplierDirectTravelTime = travelTimeMatrix.getTravelTime(mainSupplier.fromLink(), mainSupplier.toLink(), mainSupplier.departureTime());
                            double mainSupplierLatestDepartureTime = mainSupplier.departureTime() + maxWaitTime;
                            double mainSupplierLatestArrivalTime = mainSupplier.departureTime() + alpha * mainSupplierDirectTravelTime + beta;

                            DrtDemand subSupplier = tripNumberMap.get(y);//得到中间的demand
                            double subSupplierDirectTravelTime = travelTimeMatrix.getTravelTime(subSupplier.fromLink(), subSupplier.toLink(), subSupplier.departureTime());
                            double subSupplierLatestDepartureTime = subSupplier.departureTime() + maxWaitTime;
                            double subSupplierLatestArrivalTime = subSupplier.departureTime() + alpha * subSupplierDirectTravelTime + beta;

                            DrtDemand demander = tripNumberMap.get(z);//得到最短的demand
                            double demanderDirectTravelTime = travelTimeMatrix.getTravelTime(demander.fromLink(), demander.toLink(), demander.departureTime());
                            double demanderLatestDepartureTime = demander.departureTime() + maxWaitTime;
                            double demanderLatestArrivalTime = demander.departureTime() + alpha * demanderDirectTravelTime + beta;

                            //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                            double now = mainSupplier.departureTime() + stopDuration;//main supplier是o1和d1，sub supplier是o2和d2，demander是o3和d3
                            double o1o2 = travelTimeMatrix.getTravelTime(mainSupplier.fromLink(), subSupplier.fromLink(), now);
                            double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                            //o2的到达时间 <= o2最晚出发时间
                            if (arrivalTimeO2 <= subSupplierLatestDepartureTime) {
                                now = arrivalTimeO2 + stopDuration;
                                double o2o3 = travelTimeMatrix.getTravelTime(subSupplier.fromLink(), demander.fromLink(), now);
                                double arrivalTimeO3 = now + o2o3;
                                //o3的到达时间 <= o3最晚出发时间
                                if (arrivalTimeO3 <= demanderLatestDepartureTime) {
                                    now = arrivalTimeO3 + stopDuration;
                                    double arrivalTimeD3 = now + demanderDirectTravelTime;
                                    //o3到d3的时间 <= demander的最晚到达
                                    if (arrivalTimeD3 <= demanderLatestArrivalTime) {
                                        now = arrivalTimeD3 + stopDuration;
                                        double d3d2 = travelTimeMatrix.getTravelTime(demander.toLink(), subSupplier.toLink(), now);
                                        double arrivalTimeD2 = now + d3d2;
                                        //d2的到达时间 <= sub supplier的最晚达到
                                        if (arrivalTimeD2 <= subSupplierLatestArrivalTime) {
                                            now = arrivalTimeD2 + stopDuration;
                                            double d2d1 = travelTimeMatrix.getTravelTime(subSupplier.toLink(), mainSupplier.toLink(), now);
                                            double arrivalTimeD1 = now + d2d1;
                                            //d1的到达时间 <= d1最晚到达
                                            if (arrivalTimeD1 <= mainSupplierLatestArrivalTime) {
                                                numOfMatchK43 ++;
                                                pooledDemandK43.add(x);
                                                pooledDemandK43.add(y);
                                                pooledDemandK43.add(z);
                                                savingTravelTimeK43 += mainSupplierDirectTravelTime + subSupplierDirectTravelTime + demanderDirectTravelTime - (arrivalTimeD1 - mainSupplier.departureTime());
//                                                log.info(x + "," + y + "," + z);
                                                break outerLoop;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        log.info("matching (shared by 3 trips) was over");
        log.info("---------------------------------------------------------------------------------------------------------------------");
        log.info("start matching (shared by 2 trips)...");

        //判断剩下的demands在空间上2个demand是否匹配
        HashSet<Integer> pooledDemandK42 = new HashSet<>();//收集已经匹配的demand
        for (int i = 1; i <= tripPathZoneMap.size(); i++){

            if (pooledDemandK44.contains(i) || pooledDemandK43.contains(i) || pooledDemandK42.contains(i)){
                continue;
            }

            List<Integer> supplierPathZoneList = tripPathZoneMap.get(i);
            DrtDemand supplier = tripNumberMap.get(i);//get demand 1 （supplier）

            double supplierDirectTravelTime = travelTimeMatrix.getTravelTime(supplier.fromLink(), supplier.toLink(), supplier.departureTime());
            double supplierLatestDepartureTime = supplier.departureTime() + maxWaitTime;
            double supplierLatestArrivalTime = supplier.departureTime() + alpha * supplierDirectTravelTime + beta;

            for (int j = i+ 1; j <= tripPathZoneMap.size(); j++){
                List<Integer> demanderPathZoneList = tripPathZoneMap.get(j);
//                log.info("this demander number is: " + j + ", path zone size is: " + demanderPathZoneList.size());
                DrtDemand demander = tripNumberMap.get(j);//get demand2 （demander）

                if (pooledDemandK44.contains(j) || pooledDemandK43.contains(j) || pooledDemandK42.contains(j)){
                    continue;
                }

                if(Collections.indexOfSubList(supplierPathZoneList, demanderPathZoneList) != -1){//判断supplier的行程是否包含demander的行程 -> 空间上两个demand可以match -> 进而再从时间上判断

                    double demanderDirectTravelTime = travelTimeMatrix.getTravelTime(demander.fromLink(), demander.toLink(), demander.departureTime());
                    double demanderLatestDepartureTime = demander.departureTime() + maxWaitTime;
                    double demanderLatestArrivalTime = demander.departureTime() + alpha * demanderDirectTravelTime + beta;

                    //判断这两个trip在时间上是否match （o1,o2,d2,d1）
                    //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                    double now = supplier.departureTime() + stopDuration;//supplier是o1和d1，demander是o2和d2
                    double o1o2 = travelTimeMatrix.getTravelTime(supplier.fromLink(), demander.fromLink(), now);
                    double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                    //demander出发时间 <= demander最晚出发时间
                    if (arrivalTimeO2 <= demanderLatestDepartureTime) {
                        now = arrivalTimeO2 + stopDuration;
                        double arrivalTimeD2 = now + demanderDirectTravelTime;
                        //o2到d2的时间 <= demander的最晚到达
                        if (arrivalTimeD2 <= demanderLatestArrivalTime) {
                            now = arrivalTimeD2 + stopDuration;
                            double d2d1 = travelTimeMatrix.getTravelTime(demander.toLink(), supplier.toLink(), now);
                            double arrivalTimeD1 = now + d2d1;
                            //d2到d1的时间 <= d1的最晚到达
                            if (arrivalTimeD1 <= supplierLatestArrivalTime){
                                numOfMatchK42++;
                                pooledDemandK42.add(i);
                                pooledDemandK42.add(j);
                                savingTravelTimeK42 += supplierDirectTravelTime + demanderDirectTravelTime - (arrivalTimeD1 - supplier.departureTime());//(两段各自的direct时间之和 - o1到d1的时间)
                                break;
                            }
                        }
                    }
                }
            }
        }

        log.info("matching (shared by 2 trips) was over");
        log.info("matching (shared by 2 trips AND 3 trips AND 4 TRIPS (K=4)) was over");


        int numOfTotalDemands = drtDemands.size();
        double rateOfSavingCarK4 = (numOfMatchK42 + numOfMatchK43 * 2 + numOfMatchK44 * 3) / numOfTotalDemands * 100;
        double rateOfSavingTimeK4 = (savingTravelTimeK42 + savingTravelTimeK43 + savingTravelTimeK44) / totalTravelTime * 100;

        log.info("---------------------------------------------------------------------------------------------------------------------");
        log.info(" SHARED BY 2 TRIPS AND 3 TRIPS AND 4 TRIPS(K=4)");

        log.info("number of total demands is: " + numOfTotalDemands);
        log.info("number of match (shared by 2 trips) is: " + numOfMatchK42);
        log.info("number of match (shared by 3 trips) is: " + numOfMatchK43);
        log.info("number of match (shared by 4 trips) is: " + numOfMatchK44);
        log.info("---------------------------------------");
        log.info("total travel time is: " + totalTravelTime);
        log.info("saving travel time (shared by 2 trips) is: " + savingTravelTimeK42);
        log.info("saving travel time (shared by 3 trips) is: " + savingTravelTimeK43);
        log.info("saving travel time (shared by 4 trips) is: " + savingTravelTimeK44);
//        log.info("trip share rate is: " + shareRate + "%");
        log.info("---------------------------------------");
        log.info("rate of saving car is: " + rateOfSavingCarK4 + "%");
        log.info("rate of saving travel time is:" + rateOfSavingTimeK4 + "%");
    }
}
