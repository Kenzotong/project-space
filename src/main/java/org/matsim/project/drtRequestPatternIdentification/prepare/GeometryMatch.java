package org.matsim.project.drtRequestPatternIdentification.prepare;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.awt.geom.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeometryMatch {

    public static void main(String[] args) {
        // Create scenario based on config file
        String configPath = "D:\\Thesis\\drt-scenarios\\drt-scenarios\\new-york-manhattan\\manhattan_drt_config.xml";
        if (args.length != 0) {
            configPath = args[0];
        }

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
        Network network = scenario.getNetwork();

        // Getting drt setups
        double alpha = drtConfigGroup.maxTravelTimeAlpha;
        double beta = drtConfigGroup.maxTravelTimeBeta;
        double maxWaitTime = drtConfigGroup.maxWaitTime;
        double stopDuration = drtConfigGroup.stopDuration;
        System.out.println("DRT setups: alpha, beta, maxWaitTime, stopDuration " + alpha + ", " + beta + ", " + maxWaitTime + ", " + stopDuration);

        // Get drt trip set
        List<TripStructureUtils.Trip> drtTripSet = DrtTripsSet.getDrtTripsSet(config);
//        List<DrtDemand> drtDemandsSet = DrtDemandsList.getDrtDemandsList(network, population);

        Map<TripStructureUtils.Trip, Line2D> line2DMap = new HashMap<>();

        //将每个trip抽象成OD直线，并储存在一个map中
        for (TripStructureUtils.Trip trip : drtTripSet){

            double departureTime = trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);

            Link fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
            Link toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());

            Coord fromCoord = fromLink.getToNode().getCoord();
            Coord toCoord = toLink.getToNode().getCoord();

            Point2D.Double fromPoint = new Point2D.Double(fromCoord.getX(), fromCoord.getY());
            Point2D.Double toPoint = new Point2D.Double(toCoord.getX(), toCoord.getY());

            Line2D line = new Line2D.Double(fromPoint,toPoint);


            line2DMap.put(trip, line);
        }

        //创建双层map，内层为time bin为key，对应的矩形为value；外层为line为key，内层map为value
        //Map<Integer, Rectangle2D> timeBinAreaMap = new HashMap<>();
        Map<Line2D, Map<Integer, Rectangle2D>> lineTimeBinMap = new HashMap<>();

        int timeBin = 900;
        int width = 500;
        double sumArea1 = 0;
        double sumArea2 = 0;

        //按照time bin来储存所有trip形成的line所覆盖的面积
        for (TripStructureUtils.Trip trip : drtTripSet){
            //time bin为key，对应的矩形为value
            Map<Integer, Rectangle2D> timeBinAreaMap = new HashMap<>();

            Line2D line = line2DMap.get(trip);
            //获取这条trip抽象出来的line起点和终点坐标
            double x1 = line.getX1();
            double y1 = line.getY1();
            Point2D fromPoint = new Point2D.Double(x1, y1);
            double x2 = line.getX2();
            double y2 = line.getY2();
            Point2D toPoint = new Point2D.Double(x2, y2);

            double length = fromPoint.distance(toPoint);

            //获取线段的夹角(弧度)
            double angle = Math.atan2(y2 - y1, x2 - x1);
            double absAngle = Math.abs(angle);

            //确定该line的出发时间的time bin
            double departureTime = trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
            int timeBinDeparture = (int) departureTime / timeBin + 1;
            //计算出发时间这个time bin中可以行驶的距离(这个距离即为第一个矩形的高度），假设速度为6m/s；首先算时间
            double speed = 6;
            double travelTimeInFirstTimeBin = timeBinDeparture * timeBin - departureTime;
            double travelDistanceInFirstTimeBin = travelTimeInFirstTimeBin * speed;

            //计算到达时间
            double tripLength = fromPoint.distance(toPoint);
            double travelTime = tripLength / speed;
            double arrivalTime = departureTime + travelTime;
            //确定line的到达时间在哪个time bin
            int timeBinArrival = (int) arrivalTime / timeBin + 1;
            //计算到达时间这个time bin中可以行驶的距离
            double travelTimeInLastTimeBin = arrivalTime - (timeBinArrival - 1) * timeBin;
            double travelDistanceInLastTimeBin = travelTimeInLastTimeBin * speed;
            //计算time bin中的行驶距离
            double travelDistanceInThisTimeBin = timeBin * speed;
            //计算覆盖的面积
            double area1 = 2 * width * length;
            sumArea1 = sumArea1 + area1;

/*            //检验length
            double length1 = travelDistanceInLastTimeBin + travelDistanceInFirstTimeBin + (timeBinArrival - timeBinDeparture - 1) * timeBin * speed;
            System.out.println("length is: " + length + ", and length should be: " + length1);*/

            //分象限来确定第每个时间段的矩形
            //线段在第二、三象限
            if (angle <= 0) {
                //第一个time bin中左上角坐标
                double xNew = x1 - width * Math.sin(absAngle);
                double yNew = y1 - width * Math.cos(absAngle);
                for (int timeBinNum = timeBinDeparture; timeBinNum <= timeBinArrival; timeBinNum++) {
                    //计算第一个time bin
                    if (timeBinNum == timeBinDeparture) {
                        //创建矩形
                        Rectangle2D rectangle = new Rectangle2D.Double(xNew, yNew, 2 * width, travelDistanceInFirstTimeBin);
                        //以左上角坐标为中心并且旋转对应角度
                        AffineTransform transform = new AffineTransform();
                        transform.rotate(Math.PI / 2 - absAngle, xNew, yNew);
                        Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
/*                        //获取面积
                        double area = rectangle.getHeight() * rectangle.getWidth();
                        double areaTest = 2 * width * travelDistanceInFirstTimeBin;
                        System.out.println("this area is: " + area + ", and this area should be: " + areaTest);
                        sumArea2 = sumArea2 + area;*/
                        //把第一个time bin对应的矩形添加进去
                        timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                    }
                    //计算最后一个time bin
                    if (timeBinNum == timeBinArrival){
                        //斜边的长度
                        double hypotenuse = travelDistanceInFirstTimeBin + speed * timeBin * (timeBinArrival - timeBinDeparture - 1);
                        //计算最后左上角坐标
                        double xTopLift = xNew + hypotenuse * Math.cos(absAngle);
                        double yTopLift = yNew + hypotenuse * Math.sin(absAngle);
                        //创建矩形
                        Rectangle2D rectangle = new Rectangle2D.Double(xTopLift, yTopLift, 2 * width, travelDistanceInLastTimeBin);
                        //以左上角坐标为中心并且旋转对应角度
                        AffineTransform transform = new AffineTransform();
                        transform.rotate(Math.PI / 2 - absAngle, xTopLift, yTopLift);
                        Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                        //获取面积
                        double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                        sumArea2 = sumArea2 + area;
                        //把最后一个time bin对应的矩形添加进去
                        timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                    }
                    else {
                        //斜边的长度
                        double hypotenuse = travelDistanceInFirstTimeBin + speed * timeBin * (timeBinArrival - timeBinDeparture - 2);
                        //计算左上角坐标
                        double xTopLift = xNew + hypotenuse * Math.cos(absAngle);
                        double yTopLift = yNew + hypotenuse * Math.sin(absAngle);
                        //创建矩形
                        Rectangle2D rectangle = new Rectangle2D.Double(xTopLift, yTopLift, 2 * width, travelDistanceInThisTimeBin);
                        //以左上角坐标为中心并且旋转对应角度
                        AffineTransform transform = new AffineTransform();
                        transform.rotate(Math.PI / 2 - absAngle, xTopLift, yTopLift);
                        Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                        //获取面积
                        double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                        sumArea2 = sumArea2 + area;
                        //把这个time bin对应的矩形添加进去
                        timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                    }
                }
            }

            //线段在第一、四象限
             if (angle > 0){
                 //第一个time bin中左上角坐标
                 double x0 = x1 + travelDistanceInFirstTimeBin * Math.cos(absAngle);
                 double y0 = y1 - travelDistanceInFirstTimeBin * Math.sin(absAngle);
                 double xNew = x0 - width * Math.sin(absAngle);
                 double yNew = y0 - width * Math.cos(absAngle);
                 for (int timeBinNum = timeBinDeparture; timeBinNum <= timeBinArrival; timeBinNum++) {
                     //计算第一个time bin
                     if (timeBinNum == timeBinDeparture) {
                         //创建矩形
                         Rectangle2D rectangle = new Rectangle2D.Double(xNew, yNew, 2 * width, travelDistanceInFirstTimeBin);
                         //以左上角坐标为中心并且旋转对应角度
                         AffineTransform transform = new AffineTransform();
                         transform.rotate(absAngle - Math.PI / 2, xNew, yNew);
                         Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                         //获取面积
                         double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                         sumArea2 = sumArea2 + area;
                         //把第一个time bin对应的矩形添加进去
                         timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                     }
                     //计算最后一个time bin
                     if (timeBinNum == timeBinArrival){
                         //斜边的长度
                         double hypotenuse = travelDistanceInLastTimeBin + speed * timeBin * (timeBinArrival - timeBinDeparture - 1);
                         //计算最后左上角坐标
                         double xTopLift = xNew + hypotenuse * Math.cos(absAngle);
                         double yTopLift = yNew - hypotenuse * Math.sin(absAngle);
                         //创建矩形
                         Rectangle2D rectangle = new Rectangle2D.Double(xTopLift, yTopLift, 2 * width, travelDistanceInLastTimeBin);
                         //以左上角坐标为中心并且旋转对应角度
                         AffineTransform transform = new AffineTransform();
                         transform.rotate(absAngle - Math.PI / 2, xTopLift, yTopLift);
                         Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                         //获取面积
                         double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                         sumArea2 = sumArea2 + area;
                         //把最后一个time bin对应的矩形添加进去
                         timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                     }
                     else {
                         //斜边的长度
                         double hypotenuse = travelDistanceInLastTimeBin + speed * timeBin * (timeBinArrival - timeBinDeparture - 2);
                         //计算左上角坐标
                         double xTopLift = xNew + hypotenuse * Math.cos(absAngle);
                         double yTopLift = yNew + hypotenuse * Math.sin(absAngle);
                         //创建矩形
                         Rectangle2D rectangle = new Rectangle2D.Double(xTopLift, yTopLift, 2 * width, travelDistanceInThisTimeBin);
                         //以左上角坐标为中心并且旋转对应角度
                         AffineTransform transform = new AffineTransform();
                         transform.rotate(absAngle - Math.PI / 2, xTopLift, yTopLift);
                         Rectangle2D rotatedRectangle = transform.createTransformedShape(rectangle).getBounds2D();
                         //获取面积
                         double area = rotatedRectangle.getBounds2D().getHeight() * rotatedRectangle.getBounds2D().getWidth();
                         sumArea2 = sumArea2 + area;
                         //把这个time bin对应的矩形添加进去
                         timeBinAreaMap.put(timeBinNum, rotatedRectangle);
                     }
                 }
            }
            lineTimeBinMap.put(line, timeBinAreaMap);
        }
        System.out.println("检查是否所有line的time bin都存在了map中： 已存的line数量： " + lineTimeBinMap.size() + "， 应存数量为： " + drtTripSet.size());
        System.out.println("检查两种面积计算的答案是否一致： 准确值： " + sumArea1 + "， 近似值： " + sumArea2);

        double sumIntersectionArea = 0;

        //计算两个line之间同一time bin中重合的面积
        for (TripStructureUtils.Trip trip1 : drtTripSet){
            Line2D line1 = line2DMap.get(trip1);
            Map<Integer, Rectangle2D> timeBinAreaMap1 = lineTimeBinMap.get(line1);
            int trip1Index = drtTripSet.indexOf(trip1);
            for (TripStructureUtils.Trip trip2 : drtTripSet){
                Line2D line2 = line2DMap.get(trip2);
                Map<Integer, Rectangle2D> timeBinAreaMap2 = lineTimeBinMap.get(line2);
                int trip2Index = drtTripSet.indexOf(trip2);
                if (trip1Index < trip2Index) {
                    //计算两个线段的夹角
                    double angle = getAngleBetweenLines(line1, line2);
                    //筛选同方向的两条line （夹角在0-90°）
                    if (angle < Math.PI / 2) {

                        //计算同一个time bin中的重合面积
                        for (int timeBinNum : timeBinAreaMap1.keySet()) {
                            if (timeBinAreaMap2.containsKey(timeBinNum)) {
                                Rectangle2D rectangle2D1 = timeBinAreaMap1.get(timeBinNum);
                                Rectangle2D rectangle2D2 = timeBinAreaMap2.get(timeBinNum);

                                //计算两个矩形的交集
                                Area area1 = new Area(rectangle2D1);
                                Area area2 = new Area(rectangle2D2);
                                area1.intersect(area2);

                                //获取交集的近似面积
                                double intersectionArea = area1.isEmpty() ? 0 : area1.getBounds2D().getWidth() * area1.getBounds2D().getHeight();
                                sumIntersectionArea = sumIntersectionArea + intersectionArea;

                            }
                        }
                    }
                }
            }
        }
        System.out.println("该场景下重叠的面积为： " + sumIntersectionArea);
    }

    //计算两条line的夹角
    public static double getAngleBetweenLines (Line2D line1, Line2D line2){
        double x1 = line1.getX2() - line1.getX1();
        double y1 = line1.getY2() - line1.getY1();
        double x2 = line2.getX2() - line2.getX1();
        double y2 = line2.getY2() - line2.getY1();

        double dotProduct = x1 * x2 + y1 * y2;
        double magnitude1 = Math.sqrt(x1 * x1 + y1 * y1);
        double magnitude2 = Math.sqrt(x2 * x2 + y2 *y2);

        double cosTheta = dotProduct / (magnitude1 * magnitude2);

        return  Math.acos(cosTheta);
    }
}
