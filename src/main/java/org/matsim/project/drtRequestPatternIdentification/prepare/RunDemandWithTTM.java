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
import org.matsim.project.utils.LinkToLinkTravelTimeMatrix;

import java.util.*;

public class RunDemandWithTTM {

    private static final Logger log = LogManager.getLogger(RunDemandWithTTM.class);

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
        Population population = scenario.getPopulation();

        Map<String, Object> tripInfoMap = DRTPathZoneSequence.drtPathZoneMap(network, population);//得到trip的两个map
        Map<Integer, List<Integer>> tripPathZoneMap = (Map<Integer, List<Integer>>) tripInfoMap.get("tripPathZoneMap");//得到trip和其经过path的map
        Map<Integer, DrtDemand> tripNumberMap = (Map<Integer, DrtDemand>) tripInfoMap.get("tripNumberMap");

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

        int numOfMatch = 0;
        double totalTravelTime = 0;
        double pooledTravelTime = 0;

        HashSet<Integer> pooledDemand = new HashSet<>();

        //空间上和时间上判断两个行程是否match
        log.info("start matching...");
        for (int i = 1; i <= tripPathZoneMap.size(); i++){
            List<Integer> supplierPathZoneList = tripPathZoneMap.get(i);
            DrtDemand supplier = tripNumberMap.get(i);//get demand 1 （supplier）
            double directTravelTime1 = travelTimeMatrix.getTravelTime(supplier.fromLink(), supplier.toLink(), supplier.departureTime());
            totalTravelTime += directTravelTime1;
            for (int j = 1; j <= tripPathZoneMap.size(); j++){
                List<Integer> demand2PathZoneList = tripPathZoneMap.get(j);
                DrtDemand demander = tripNumberMap.get(j);//get demand2 （demander）
                if(supplier == demander){
                    continue;
                }
                //检查demander(短)是否作为demander之前已经被pick up了
                if(pooledDemand.contains(j)){
                    continue;
                }

                if(Collections.indexOfSubList(supplierPathZoneList, demand2PathZoneList) != -1){//判断supplier的行程是否包含demander的行程 -> 空间上两个demand可以match -> 进而再从时间上判断
                    double directTravelTime2 = travelTimeMatrix.getTravelTime(demander.fromLink(), demander.toLink(), demander.departureTime());

                    double supplierLatestDepartureTime = supplier.departureTime() + maxWaitTime;
                    double demanderLatestDepartureTime = demander.departureTime() + maxWaitTime;

                    double supplierLatestArrivalTime = supplier.departureTime() + alpha * directTravelTime1 + beta;
                    double demanderLatestArrivalTime = demander.departureTime() + alpha * directTravelTime2 + beta;

                    //判断这两个trip在时间上是否match （o1,o2,d2,d1）
                    //O2的最晚出发时间 > O1出发从O1到O2的到达时间（理想情况）
                    double now = supplier.departureTime() + stopDuration;//supplier是o1和d1，demander是o2和d2
                    double o1o2 = travelTimeMatrix.getTravelTime(supplier.fromLink(), supplier.fromLink(), now);
                    double arrivalTimeO2 = now + o1o2; //o2 实际到达时间
                    //demander出发时间 <= demander最晚出发时间
                    if (arrivalTimeO2 <= demanderLatestDepartureTime) {
                        now = arrivalTimeO2 + stopDuration;
                        double arrivalTimeD2 = now + directTravelTime2;
                        //o2到d2的时间 <= demander的最晚到达
                        if (arrivalTimeD2 <= demanderLatestArrivalTime) {
                            now = arrivalTimeD2 + stopDuration;
                            double d2d1 = travelTimeMatrix.getTravelTime(demander.toLink(), supplier.toLink(), now);
                            double arrivalTimeD1 = now + d2d1;
                            //d2到d1的时间 <= d1的最晚到达
                            if (arrivalTimeD1 <= supplierLatestArrivalTime){
                                numOfMatch++;
                                pooledDemand.add(j);//demand j作为demander 2已经被picked up，加入该列表，之后跳过
                                pooledTravelTime += directTravelTime2;//demand 2 (demander)成功拼车，行驶时间计算进pooled的时间中
                                break;
                            }
                        }
                    }
                }
            }
        }
//        CoordUtils.calcEuclideanDistance(from Coord, toCoord) //计算两点之间的距离 to node到to node

//        int numOfTotalPairs = (drtDemands.size() * drtDemands.size() - 1)/2;
        int numOfTotalDemands = drtDemands.size();
//        double shareRate = (double) numOfMatch / numOfTotalPairs * 100;
        double rateOfSavingCar = (double) numOfMatch / numOfTotalDemands * 100;
        double rateOfSavingTime = pooledTravelTime / totalTravelTime * 100;

//        log.info("number of total pairs is: " + numOfTotalPairs);
        log.info("number of total demands is: " + numOfTotalDemands);
        log.info("number of match is: " + numOfMatch);
        log.info("---------------------------------------");
        log.info("total travel time is: " + totalTravelTime);
        log.info("saving travel time is: " + pooledTravelTime);
//        log.info("trip share rate is: " + shareRate + "%");
        log.info("---------------------------------------");
        log.info("rate of saving car is: " + rateOfSavingCar + "%");
        log.info("rate of saving travel time is:" + rateOfSavingTime + "%");


    }
}
