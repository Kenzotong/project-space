package org.matsim.project.drtRequestPatternIdentification.prepare;


import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import java.util.HashMap;
import java.util.Map;



public class LinkZoneMap {

    public static void main(String[] args) {
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\New-York-Manhattan\\nyc-drt.config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }
        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        linkZoneMap(config);
    }

    public static Map<Id<Link>,Integer> linkZoneMap(Config config){

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();// 获取MATSim中的Network对象

        // 获取网络的边界坐标
        Coord minCoord = getMinCoord(network);
        Coord maxCoord = getMaxCoord(network);

        double zoneSize = 1000.0; // 1km的正方形区域边长
        double minX = minCoord.getX();
        double minY = minCoord.getY();
        double maxX = maxCoord.getX();
        double maxY = maxCoord.getY();
        int zoneId = 1;

        Map<Id<Link>,Integer> linkZoneMap = new HashMap<>();//创建一个map，以Link为map中的key，Link所在的Zone作为value

        for (double x = minX; x < maxX; x += zoneSize) {
            for (double y = minY; y < maxY; y += zoneSize) {
                Coord zoneMinCoord = new Coord(x, y);
                Coord zoneMaxCoord = new Coord(x + zoneSize, y + zoneSize);

                //遍历所有link，并通过link的start node判断link是否在当前zone中,将link按照zone分类
                for(Link link: network.getLinks().values()){
//                    String linkIdString = link.getId().toString();//linkId作为字符串
                    Id<Link> linkId = link.getId();//linkId作为id类
                    Coord startCoord = link.getFromNode().getCoord();
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
