package org.matsim.project.drtRequestPatternIdentification.prepare;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.project.drtRequestPatternIdentification.basicStructures.DrtDemand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class LinkZoneMap {

//    public static void main(String[] args) {
//        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
//        if (args.length != 0) {
//            configPath = args[0];
//        }
//        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
//        linkZoneMap(config);
//    }

    private static final Logger log = LogManager.getLogger(LinkZoneMap.class);

    public static Map<Id<Link>,Integer> linkZoneMap(Network network, Population population){

        log.info("---------------------------------------");
        log.info("trip average distance is started calculating...");
        double totalDistance = 0;
        List<DrtDemand> drtDemands = DrtDemandsList.getDrtDemandsList(network, population);
//        System.out.println("there are " + drtDemands.size() + " demands");
        //得到该场景的drt行驶总距离
        for (DrtDemand demand : drtDemands){
            double distance = CoordUtils.calcEuclideanDistance(demand.fromLink().getToNode().getCoord(), demand.toLink().getToNode().getCoord());//该demand的行程距离
//            System.out.println("this demand is: " + distance + " meters");
            totalDistance += distance;
        }
        double averageDistance = totalDistance / drtDemands.size();
        log.info("trip average distance was successfully created");
        log.info("---------------------------------------");

        // 获取网络的边界坐标
        Coord minCoord = getMinCoord(network);
        Coord maxCoord = getMaxCoord(network);

        double zoneSize = averageDistance * 0.1; // 平均行驶里程的10%作为zone的尺寸
        log.info("zone size of this network is: " + zoneSize);
        double minX = minCoord.getX();
        double minY = minCoord.getY();
        double maxX = maxCoord.getX();
        double maxY = maxCoord.getY();
        int zoneId = 1;

        Map<Id<Link>,Integer> linkZoneMap = new HashMap<>();//创建一个map，以Link为map中的key，Link所在的Zone作为value

        log.info("---------------------------------------");
        log.info("link zone map is started creating...");
        for (double x = minX; x < maxX; x += zoneSize) {
            for (double y = minY; y < maxY; y += zoneSize) {
                Coord zoneMinCoord = new Coord(x, y);
                Coord zoneMaxCoord = new Coord(x + zoneSize, y + zoneSize);

                //遍历所有link，并通过link的to node判断link是否在当前zone中,将link按照zone分类
                for(Link link: network.getLinks().values()){
//                    String linkIdString = link.getId().toString();//linkId作为字符串
                    Id<Link> linkId = link.getId();//linkId作为id类
                    Coord startCoord = link.getToNode().getCoord();
                    if(zoneMaxCoord.getX() >= startCoord.getX() && startCoord.getX() >= zoneMinCoord.getX()
                            && zoneMaxCoord.getY() >= startCoord.getY() && startCoord.getY() >= zoneMinCoord.getY()){
                        linkZoneMap.put(linkId,zoneId);
                    }
                }
                zoneId++;
            }
        }

//        检验map是否正确填充
//        for (Map.Entry<Link, Integer> entry : linkZoneMap.entrySet()) {
//            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
//            System.out.println(linkZoneMap.get(entry.getKey()));
//        }

        log.info("link zone map was successfully created");
        log.info("---------------------------------------");
        return linkZoneMap;
    }

    private static Coord getMinCoord(Network network) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;

        for (Node node : network.getNodes().values()) {
            Coord coord = node.getCoord();
            if (coord.getX() < minX) {
                minX = coord.getX();
            }
            if (coord.getY() < minY) {
                minY = coord.getY();
            }
        }

        return new Coord(minX, minY);
    }

    private static Coord getMaxCoord(Network network) {
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (Node node : network.getNodes().values()) {
            Coord coord = node.getCoord();
            if (coord.getX() > maxX) {
                maxX = coord.getX();
            }
            if (coord.getY() > maxY) {
                maxY = coord.getY();
            }
        }

        return new Coord(maxX, maxY);
    }

}
