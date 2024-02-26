package org.matsim.project.drtRequestPatternIdentification.prepare;

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
import org.matsim.project.drtRequestPatternIdentification.prepare.algorithm2.DRTPathZoneSequence;
import org.matsim.project.utils.LinkToLinkTravelTimeMatrix;

import java.util.*;

public class RunDemandWithTTM {

    private static final Logger log = LogManager.getLogger(RunDemandWithTTM.class);

    public static void main(String[] args) {

        // Create scenario based on config file
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\\\vulkaneifel\\vulkaneifel_drt_config-5.xml";
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

        double numOfMatchK22 = 0;
        double numOfMatchK32 = 0;
        double numOfMatchK33 = 0;
        double totalTravelTime = 0;
        double savingTravelTimeK22 = 0;
        double savingTravelTimeK32 = 0;
        double savingTravelTimeK33 = 0;

        //(K=2) 空间上和时间上判断两个行程是否match
        HashSet<Integer> pooledDemandK22 = new HashSet<>();

        log.info("---------------------------------------");
        log.info("start matching (ONLY shared by 2 trips)...");
        for (int i = 1; i <= tripPathZoneMap.size(); i++){
            List<Integer> supplierPathZoneList = tripPathZoneMap.get(i);
            DrtDemand supplier = tripNumberMap.get(i);//get demand 1 （supplier）

            double supplierDirectTravelTime = travelTimeMatrix.getTravelTime(supplier.fromLink(), supplier.toLink(), supplier.departureTime());
            double supplierLatestDepartureTime = supplier.departureTime() + maxWaitTime;
            double supplierLatestArrivalTime = supplier.departureTime() + alpha * supplierDirectTravelTime + beta;

            totalTravelTime += supplierDirectTravelTime;

            //检查supplier(长)是否之前已经被分配了
            if(pooledDemandK22.contains(i)){
                continue;
            }

            for (int j = i + 1; j <= tripPathZoneMap.size(); j++){
                //检查demander(短)是否之前已经被分配了
                if(pooledDemandK22.contains(j)){
                    continue;
                }

                List<Integer> demanderPathZoneList = tripPathZoneMap.get(j);
//                log.info("this demander number is: " + j + ", path zone size is: " + demanderPathZoneList.size());
                DrtDemand demander = tripNumberMap.get(j);//get demand2 （demander）
//                if(supplier == demander){
//                    continue;
//                }

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
                                numOfMatchK22++;
                                pooledDemandK22.add(i);//demand i作为 supplier 已经被分配了，加入该列表，之后跳过
                                pooledDemandK22.add(j);//demand j作为 demander 已经被分配，加入该列表，之后跳过
                                savingTravelTimeK22 += supplierDirectTravelTime + demanderDirectTravelTime - (arrivalTimeD1 - supplier.departureTime()) ;//demand 2 (demander)成功拼车，行驶时间计算进pooled的时间中 (两段各自的direct时间之和 - o1到d1的时间)
                                break;
                            }
                        }
                    }
                }
            }
        }
        log.info("matching (ONLY shared by 2 trips) was over");

        //        int numOfTotalPairs = (drtDemands.size() * drtDemands.size() - 1)/2;
        int numOfTotalDemands = drtDemands.size();
//        double shareRate = (double) numOfMatch / numOfTotalPairs * 100;
        double rateOfSavingCar = numOfMatchK22 / numOfTotalDemands * 100;
        double rateOfSavingTime = savingTravelTimeK22 / totalTravelTime * 100;

//        log.info("number of total pairs is: " + numOfTotalPairs);
        log.info("---------------------------------------");
        log.info("ONLY SHARED BY 2 TRIPS (K=2)");
        log.info("number of total demands is: " + numOfTotalDemands);
        log.info("number of match is: " + numOfMatchK22);
        log.info("---------------------------------------");
        log.info("total travel time is: " + totalTravelTime);
        log.info("saving travel time is: " + savingTravelTimeK22);
//        log.info("trip share rate is: " + shareRate + "%");
        log.info("---------------------------------------");
        log.info("rate of saving car is: " + rateOfSavingCar + "%");
        log.info("rate of saving travel time is:" + rateOfSavingTime + "%");





       /*//(K=3) 空间上和时间上判断三个行程是否match
        log.info("-----------------------------------------------------------------------------------------------------------------------");
        log.info("start matching (shared by 2 trips AND 3 trips)...");

        Map<Integer,List<Integer>> shareableTripPairsMap = new LinkedHashMap<>();//key为supplier，value为demander,有顺序的
        HashSet<Integer> pooledDemandK33 = new HashSet<>();//收集已经匹配的demand

        log.info("shareable trip pairs map is started creating (shared by 2 trips)...");

        //收集所有可共享的两两配对
        for (int i = 1; i <= tripPathZoneMap.size(); i++){
            List<Integer> shareableTrips = new ArrayList<>();
            List<Integer> supplierPathZoneList = tripPathZoneMap.get(i);
            DrtDemand supplier = tripNumberMap.get(i);//get demand 1 （supplier）

            double supplierDirectTravelTime = travelTimeMatrix.getTravelTime(supplier.fromLink(), supplier.toLink(), supplier.departureTime());
            double supplierLatestDepartureTime = supplier.departureTime() + maxWaitTime;
            double supplierLatestArrivalTime = supplier.departureTime() + alpha * supplierDirectTravelTime + beta;

//            totalTravelTime += supplierDirectTravelTime;
            for (int j = i + 1; j <= tripPathZoneMap.size(); j++){
                List<Integer> demanderPathZoneList = tripPathZoneMap.get(j);
                DrtDemand demander = tripNumberMap.get(j);//get demand2 （demander）
//                if(supplier == demander){
//                    continue;
//                }

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
                shareableTripPairsMap.put(i, shareableTrips);//获得map：supplier作为key，所有可以和它匹配的demander为value
            }
//            log.info("shareable trip pairs map was: " + shareableTripPairsMap);
        }
        boolean a = shareableTripPairsMap.get(1).contains(2876);
        boolean b = shareableTripPairsMap.get(2876).contains(4637);

        log.info(a + ", " + b);


        log.info("shareable trip pairs map was successfully created (shared by 2 trips)");
        log.info("---------------------------------------");
        log.info("start matching (shared by 3 trips)...");

        //判断空间上三个demand是否匹配
        for (int x : shareableTripPairsMap.keySet()){
            //判断是否已经匹配过了
            if(pooledDemandK33.contains(x)){
                continue;
            }

            outerLoop: for (int i = 0; i <= shareableTripPairsMap.get(x).size() - 1; i++) {
                int y = shareableTripPairsMap.get(x).get(i);
                //判断是否已经匹配过了
                if (pooledDemandK33.contains(y)) {
                    continue;
                }

                if (shareableTripPairsMap.containsKey(y)) {
                    for (int j = 0; j <= shareableTripPairsMap.get(y).size() - 1; j++) {
                        int z = shareableTripPairsMap.get(y).get(j);
                        //判断是否已经匹配过了
                        if (pooledDemandK33.contains(z)) {
                            continue;
                        }

                        if (shareableTripPairsMap.get(x).contains(z)) {
                            ////判断这三个trip在时间上是否match （o1,o2,o3,d3,d2,d1）
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
                                                numOfMatchK33 ++;
                                                pooledDemandK33.add(x);
                                                pooledDemandK33.add(y);
                                                pooledDemandK33.add(z);
                                                savingTravelTimeK33 += mainSupplierDirectTravelTime + subSupplierDirectTravelTime + demanderDirectTravelTime - (arrivalTimeD1 - mainSupplier.departureTime()); //三段各自direct的时间之和 - (d1的实际到达时间 - o1的出发时间)
                                                log.info(x + "," + y + "," + z);
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
        }*/
        log.info("------------------------------------------------------------------------------");
        //不用map计算k=3
        log.info("matching (shared by 3 trips) starting...");
        HashSet<Integer> pooledDemandK33 = new HashSet<>();

        for (int x = 1; x <= drtDemands.size(); x++) {
            //判断是否匹配过了
            DrtDemand demand1 = tripNumberMap.get(x);//得到第1长的demand
            double demand1DirectTravelTime = travelTimeMatrix.getTravelTime(demand1.fromLink(), demand1.toLink(), demand1.departureTime());

//            totalTravelTime += demand1DirectTravelTime;

            if (pooledDemandK33.contains(x)) {
                continue;
            }
            List<Integer> pathListA = tripPathZoneMap.get(x);
            outerLoop:
            for (int y = x + 1; y <= drtDemands.size(); y++) {
                //判断是否匹配过了
                if (pooledDemandK33.contains(y)) {
                    continue;
                }
                List<Integer> pathListB = tripPathZoneMap.get(y);
                if (Collections.indexOfSubList(pathListA, pathListB) != -1) {//判断x的行程是否包含y的行程 -> 空间上两个demand可以match
                    for (int z = y + 1; z <= drtDemands.size(); z++) {
                        //判断是否匹配过了
                        if (pooledDemandK33.contains(z)) {
                            continue;
                        }
                        List<Integer> pathListC = tripPathZoneMap.get(z);
                        if (Collections.indexOfSubList(pathListB, pathListC) != -1) {//判断y的行程是否包含z的行程 -> 空间上两个demand可以match
                            double demand1LatestDepartureTime = demand1.departureTime() + maxWaitTime;
                            double demand1LatestArrivalTime = demand1.departureTime() + alpha * demand1DirectTravelTime + beta;

                            DrtDemand demand2 = tripNumberMap.get(y);//得到第2长的demand
                            double demand2DirectTravelTime = travelTimeMatrix.getTravelTime(demand2.fromLink(), demand2.toLink(), demand2.departureTime());
                            double demand2LatestDepartureTime = demand2.departureTime() + maxWaitTime;
                            double demand2LatestArrivalTime = demand2.departureTime() + alpha * demand2DirectTravelTime + beta;

                            DrtDemand demand3 = tripNumberMap.get(z);//得到第3长的demand
                            double demand3DirectTravelTime = travelTimeMatrix.getTravelTime(demand3.fromLink(), demand3.toLink(), demand3.departureTime());
                            double demand3LatestDepartureTime = demand3.departureTime() + maxWaitTime;
                            double demand3LatestArrivalTime = demand3.departureTime() + alpha * demand3DirectTravelTime + beta;

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
                                    double arrivalTimeD3 = now + demand3DirectTravelTime;
                                    //o3到d3的时间 <= demand3的最晚到达
                                    if (arrivalTimeD3 <= demand3LatestArrivalTime) {
                                        now = arrivalTimeD3 + stopDuration;
                                        double d3d2 = travelTimeMatrix.getTravelTime(demand3.toLink(), demand2.toLink(), now);
                                        double arrivalTimeD2 = now + d3d2;
                                        //d3到d2的时间 <= demand2的最晚到达
                                        if (arrivalTimeD2 <= demand2LatestArrivalTime) {
                                            now = arrivalTimeD2 + stopDuration;
                                            double d2d1 = travelTimeMatrix.getTravelTime(demand2.toLink(), demand1.toLink(), now);
                                            double arrivalTimeD1 = now + d2d1;
                                            //d2到d1的时间 <= demand1的最晚到达
                                            if (arrivalTimeD1 <= demand1LatestArrivalTime) {
                                                numOfMatchK33++;
                                                pooledDemandK33.add(x);
                                                pooledDemandK33.add(y);
                                                pooledDemandK33.add(z);
//                                                log.info(x + ", " + y + ", " + z);
                                                savingTravelTimeK33 += demand1DirectTravelTime + demand2DirectTravelTime + demand3DirectTravelTime - (arrivalTimeD1 - demand1.departureTime()); //3段各自direct的时间之和 - (d1的实际到达时间 - o1的出发时间)
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
        log.info("---------------------------------------");
        log.info("start matching (shared by 2 trips)...");

        HashSet<Integer> pooledDemandK32 = new HashSet<>();//收集已经匹配的demand

        //剩下的两两配对
        for (int i = 1; i <= tripPathZoneMap.size(); i++){
            //检查supplier(长)是否在k=3时已经被分配了
            if(pooledDemandK33.contains(i)){
                continue;
            }
            //检查supplier(长)是否在k=2时已经被分配了
            if(pooledDemandK32.contains(i)){
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
//                if(supplier == demander){
//                    continue;
//                }
                //检查demander(短)是否在k=3时已经被分配了
                if(pooledDemandK33.contains(j)){
                    continue;
                }
                //检查demander(短)是否在k=2时已经被分配了
                if(pooledDemandK32.contains(j)){
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
                                numOfMatchK32++;
                                pooledDemandK32.add(i);//demand i作为 supplier 已经被分配了，加入该列表，之后跳过
                                pooledDemandK32.add(j);//demand j作为 demander 已经被分配，加入该列表，之后跳过
                                savingTravelTimeK32 += supplierDirectTravelTime + demanderDirectTravelTime - (arrivalTimeD1 - supplier.departureTime());//(两段各自的direct时间之和 - o1到d1的时间)
                                break;
                            }
                        }
                    }
                }
            }
        }
        log.info("matching (shared by 2 trips) was over");
        log.info("matching (shared by 2 trips AND 3 trips) was over");


        //        int numOfTotalPairs = (drtDemands.size() * drtDemands.size() - 1)/2;
//        int numOfTotalDemands = drtDemands.size();
//        double shareRate = (double) numOfMatch / numOfTotalPairs * 100;
        double rateOfSavingCarK3 = (numOfMatchK32 + numOfMatchK33 * 2) / numOfTotalDemands * 100;
        double rateOfSavingTimeK3 = (savingTravelTimeK32 + savingTravelTimeK33) / totalTravelTime * 100;

//        log.info("number of total pairs is: " + numOfTotalPairs);
        log.info("---------------------------------------");
        log.info(" SHARED BY 2 TRIPS AND 3 TRIPS (K=3)");
        log.info("number of total demands is: " + numOfTotalDemands);
        log.info("number of match (shared by 2 trips) is: " + numOfMatchK32);
        log.info("number of match (shared by 3 trips) is: " + numOfMatchK33);
        log.info("---------------------------------------");
        log.info("total travel time is: " + totalTravelTime);
        log.info("saving travel time (shared by 2 trips) is: " + savingTravelTimeK32);
        log.info("saving travel time (shared by 3 trips) is: " + savingTravelTimeK33);
//        log.info("trip share rate is: " + shareRate + "%");
        log.info("---------------------------------------");
        log.info("rate of saving car is: " + rateOfSavingCarK3 + "%");
        log.info("rate of saving travel time is:" + rateOfSavingTimeK3 + "%");
    }
}
