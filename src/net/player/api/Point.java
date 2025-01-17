package net.player.api;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.utils.Config;


import com.smallaswater.easysql.mysql.data.SqlData;
import com.smallaswater.easysql.mysql.data.SqlDataList;
import com.smallaswater.easysql.mysql.utils.ChunkSqlType;
import com.smallaswater.easysql.v3.mysql.manager.SqlManager;
import net.player.PlayerPoint;
import net.player.api.events.PlayerAddPointEvent;
import net.player.api.events.PlayerPayTargetEvent;
import net.player.api.events.PlayerReducePointEvent;
import net.player.api.events.PlayerSetPointEvent;


import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * API类
 * @author 若水
 */
public class Point {

    private static ExecutorService service = Executors.newCachedThreadPool();

    private static LinkedHashMap<String, Double> cachePoint = new LinkedHashMap<>();

    public static void addPoint(UUID player,double point){
        if(point > 0){
            PlayerAddPointEvent event = new PlayerAddPointEvent(player,point);
            Server.getInstance().getPluginManager().callEvent(event);
        }

    }

    public static void addPoint(String player,double point){
        if(point > 0){
            PlayerAddPointEvent event = new PlayerAddPointEvent(player,point);
            Server.getInstance().getPluginManager().callEvent(event);
        }

    }

    public static void addPoint(Player player, double point){
        addPoint(player.getUniqueId(),point);
    }

    public static void setPoint(String player,double point){
        PlayerSetPointEvent event = new PlayerSetPointEvent(player,point);
        Server.getInstance().getPluginManager().callEvent(event);

    }

    public static void setPoint(UUID player,double point){
        PlayerSetPointEvent event = new PlayerSetPointEvent(player,point);
        Server.getInstance().getPluginManager().callEvent(event);
    }

    public static void setPoint(Player player, double point){
        setPoint(player.getUniqueId(),point);
    }

    public static boolean reducePoint(String player,double point){
        if(point > 0){
            if(canReduce(player,point)){
                PlayerReducePointEvent event = new PlayerReducePointEvent(player,point);
                Server.getInstance().getPluginManager().callEvent(event);
                return true;
            }
        }
        return false;
    }

    public static Player getPlayer(Object uuid){
        Player player = null;
        if(uuid instanceof Player){
            player = (Player) uuid;
        }
        if(uuid instanceof String){
            if(P.matcher(uuid.toString()).matches()){
                player = Server.getInstance().getOfflinePlayer(UUID.fromString(uuid.toString())).getPlayer();
            }else{
                player = Server.getInstance().getPlayer(uuid.toString());
            }
        }
        if(uuid instanceof UUID){
            player = Server.getInstance().getOfflinePlayer((UUID) uuid).getPlayer();
        }
        return player;
    }

    private static String getCacheName(Object uuid){
        Player player = getPlayer(uuid);
        String name;
        if(player != null){
            if (PlayerPoint.getInstance().canSaveUUID()) {
                name = player.getUniqueId().toString();
            } else {
                name = player.getName();
            }
        }else{
            if (PlayerPoint.getInstance().canSaveUUID()) {
                name = uuid.toString();
            } else {
                if (uuid instanceof UUID) {
                    name = Point.getPlayerNameByUUID((UUID) uuid);
                } else {
                    name = uuid.toString();
                }
            }
        }
        return name;
    }

    private static void cache(Object uuid){
        double d;
        try {
            SqlManager enable = PlayerPoint.getInstance().getEnable();
            String s = null;
            Player player = getPlayer(uuid);
            if (player != null) {
//                SqlDataManager manager = enable.getManager().getSqlManager();
                SqlDataList<SqlData> o = enable.getData("SELECT count FROM "+PlayerPoint.TABLE_NAME+" WHERE user = ?",new ChunkSqlType(1, player.getUniqueId().toString()));
//                SqlDataList<SqlData> o = enable.selectExecute("count", null, "user = ?", );
                if (o != null) {
                    SqlData data = o.get();
                    if (data != null) {
                        s = data.getString("count", "0.0D");
                    }
                }
            } else {
//                SqlDataManager manager = enable.getManager().getSqlManager();
                SqlDataList<SqlData> o = enable.getData("SELECT count FROM "+PlayerPoint.TABLE_NAME+" WHERE user = ?", new ChunkSqlType(1, uuid.toString()));
                if (o != null) {
                    SqlData data = o.get();
                    if (data != null) {
                        s = data.getString("count", "0.0D");
                    }
                }
            }
            if (s == null) {
                d =  0.0D;
            }else {
                d = Double.parseDouble(s);
            }
        }catch (Exception e){
            d =  0.0D;
        }
        cachePoint.put(getCacheName(uuid),d);
    }
    public static double getPoint(Object uuid){
        return getPoint(uuid,false);
    }

    public static double getPoint(Object uuid,boolean updata){
        Player player;
        if(PlayerPoint.getInstance().isCanLoadSql()) {
            double d;
            if(updata){
                cache(uuid);
            }else{
                if(cachePoint.containsKey(getCacheName(uuid))){
                    service.execute(() -> cache(uuid));
                }else{
                    cache(uuid);
                }
            }

            d = cachePoint.get(getCacheName(uuid));
            return d;

        }else{
            Config config = PlayerPoint.getInstance().getPointConfig();
            player = getPlayer(uuid);
            if (player != null) {
                if (PlayerPoint.getInstance().canSaveUUID()) {
                    return config.getDouble(player.getUniqueId().toString());
                } else {
                    return config.getDouble(player.getName());
                }
            } else {
                if (PlayerPoint.getInstance().canSaveUUID()) {
                    if (uuid instanceof String) {
                        UUID uuid1 = Point.getUUIDByPlayerName(uuid.toString());
                        if (uuid1 != null) {
                            return config.getDouble(uuid1.toString());
                        }
                    } else {
                        return config.getDouble(uuid.toString());
                    }
                } else {
                    if (uuid instanceof UUID) {
                        return config.getDouble((Point.getPlayerNameByUUID((UUID) uuid)));
                    } else {
                        return config.getDouble(uuid.toString());
                    }
                }
            }

        }

        return 0.0D;
    }

    public static void setPoint(Object uuid,double point){
        service.execute(() -> {
            if(PlayerPoint.getInstance().isCanLoadSql()) {
                Player player = getPlayer(uuid);
                String s = uuid.toString();
                SqlManager enable = PlayerPoint.getInstance().getEnable();
                if(player != null){
                    s = player.getUniqueId().toString();
                }
                if(enable.isExistsData(PlayerPoint.TABLE_NAME,"user",s)) {
                    enable.setData(PlayerPoint.TABLE_NAME,new SqlData("user", s ).put("count",point),new SqlData("user", s));
                }else{
                    enable.insertData(PlayerPoint.TABLE_NAME,new SqlData("count",   point ).put("user",  s ));

                }
            }else {
                Config config = PlayerPoint.getInstance().getPointConfig();
                Player player = getPlayer(uuid);
                if (player != null) {
                    if (PlayerPoint.getInstance().canSaveUUID()) {
                        config.set(player.getUniqueId().toString(), point);
                    } else {
                        config.set(player.getName(), point);
                    }
                } else {
                    if (PlayerPoint.getInstance().canSaveUUID()) {
                        if (uuid instanceof String) {
                            UUID uuid1 = Point.getUUIDByPlayerName(uuid.toString());
                            if (uuid1 != null) {
                                config.set(uuid1.toString(), point);
                            }
                        } else {
                            config.set(uuid.toString(), point);
                        }
                    } else {
                        if (uuid instanceof UUID) {
                            config.set((Point.getPlayerNameByUUID((UUID) uuid)), point);
                        } else {
                            config.set(uuid.toString(), point);
                        }
                    }
                }
                config.save();
            }
        });



    }
    public static boolean canReduce(Object uuid,double point){
        if(point > 0){
            double old = getPoint(uuid,true);
            return (old >= point);
        }
        return false;
    }

    /**
     * @return 兑换比例
     * */
    public static int getMax(){
        return PlayerPoint.getInstance().getConfig().getInt("兑换EconomyAPI比例",100);
    }

    public static boolean reducePoint(UUID player,double point){
       if(canReduce(player,point)){
           PlayerReducePointEvent event = new PlayerReducePointEvent(player,point);
           Server.getInstance().getPluginManager().callEvent(event);

           return true;
       }
        return false;
    }



    public static int playerPayTargetPoint(Player player, Player target, double point){
        return playerPayTargetPoint(player.getUniqueId(),target.getUniqueId(),point);
    }

    public static int playerPayTargetPoint(UUID player, UUID target, double point){
        if(player != null && target != null){
            if(myPoint(player) >= point){
                if(player.equals(target)){
                    return 0;
                }
                PlayerPayTargetEvent event = new PlayerPayTargetEvent(player,target,point);
                Server.getInstance().getPluginManager().callEvent(event);
                if(!event.isCancelled()){
                    reducePoint(player,point);
                    addPoint(target,point);
                }

                return 1;
            }else{
                return 0;
            }
        }

        return -1;
    }





    public static boolean reducePoint(Player player, double point){
        return reducePoint(player.getUniqueId(),point);
    }

    public static String getPointName(){
        return PlayerPoint.getInstance().getPointName();
    }

    public static double getDefaultPoint(){
        return PlayerPoint.getInstance().getDefaultPoint();
    }

    public static double getMaxPoint(){
        return PlayerPoint.getInstance().getMaxPoint();
    }

    public static double myPoint(UUID player){
        return getPoint(player);
    }

    public static double myPoint(Player player){
        return getPoint(player);
    }

    public static double myPoint(String player){
        return getPoint(player);
    }

    public static String getPlayerNameByUUID(UUID uuid){
        return Server.getInstance().getOfflinePlayer(uuid).getName();
    }

    private static final Pattern P =  Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.dat$");
    public static UUID getUUIDByPlayerName(String name){
        Player player = Server.getInstance().getPlayer(name);
        if(player != null){
            return player.getUniqueId();
        }else{
            File dataDirectory = new File(Server.getInstance().getDataPath(), "players/");
            File[] files = dataDirectory.listFiles((file) -> {
                String names = file.getName();
                return P.matcher(names).matches() && names.endsWith(".dat");
            });
            if (files != null) {
                for(File file:files){
                    String uu = file.getName();
                    uu = uu.substring(0, uu.length() - 4);
                    UUID uuid = UUID.fromString(uu);
                    if(Server.getInstance().getOfflinePlayer(uuid).getName().equals(name)){
                        return uuid;
                    }
                }
            }
        }

        return null;
    }
    public static boolean isRightNumberPoint(String point){
        try {
            double p = Double.parseDouble(point);
            return p > 0;
        }catch (Exception e){
            return false;
        }
    }

    /**
     * String 为 UUID
     * 排行榜
     * */
    public static HashMap<String,Number> getPlayerRankingList(){
        return toRankList(getPlayerAllMoney());

    }

    public static LinkedHashMap<String, Double> getPlayerAllMoney(){
        Map<String,Object> map;
        if(!PlayerPoint.getInstance().isCanLoadSql()) {
            map = PlayerPoint.getInstance().getPointConfig().getAll();
        }else{
            return cachePoint;
        }
        LinkedHashMap<String,Double> rank = new LinkedHashMap<>();
        for(String name:map.keySet()){
            rank.put(name, (double)map.get(name));
        }
        return rank;

    }


    private static LinkedHashMap<String,Number> toRankList(LinkedHashMap<String, ? extends Number> map){
        LinkedHashMap<String,Number> rank = new LinkedHashMap<>();
        HashMap<String,Number> map1 = new LinkedHashMap<>();
        for(String n:map.keySet()){
            Number num = map.get(n);
            if(num instanceof Integer) {
                map1.put(n, num);
            }else{
                if(num instanceof Double){
                    map1.put(n, Integer.parseInt(new java.text.DecimalFormat("0").format(num)));
                }
            }
        }
        Comparator<Map.Entry<String, Number>> valCmp = (o1, o2) -> {
            // TODO Auto-generated method stub
            return Integer.parseInt(o2.getValue().toString()) - Integer.parseInt(o1.getValue().toString());
        };
        List<Map.Entry<String, Number>> list = new ArrayList<>(map1.entrySet());
        list.sort(valCmp);
        for(Map.Entry<String,Number> ma:list){
            rank.put(ma.getKey(),ma.getValue());
        }

        return rank;
    }
}
