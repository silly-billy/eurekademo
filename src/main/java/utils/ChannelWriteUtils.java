package utils;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class ChannelWriteUtils {

    //<web端pos机代码,channel>
    public static Map webContainer = new ConcurrentHashMap<String,Channel>();
    //<TCP端pos机代码,channel>
    public static Map deviceContainer = new ConcurrentHashMap<String,Channel>();

    private static boolean isActive(String appletID){
        //todo 查询同一pos机代码的channel是否存活
       return webContainer.containsKey(appletID)&&deviceContainer.containsKey(appletID)?true:false;
    }

    private static Channel queryPosChannel(Channel channel,String appletID){
        //todo 查询对于POS机的连接
        if(isActive(appletID)){
            Channel webChannel = (Channel) webContainer.get(appletID);
            Channel deviceChannel = (Channel) deviceContainer.get(appletID);
            return Objects.equals(webChannel,channel)?deviceChannel:webChannel;
        }
        log.error("ChannelWriteUtils.isActive return false");
        return null;
    }

    /**
     * @Description //TODO  webSocket和tcpSocket通信
     * @Date  2019/11/20
     * @Param msg channel
     * @return
     */
    public static void channelWrite(Object msg,Channel channel,String appletID){
        Channel choooseChannel = queryPosChannel(channel,appletID);
        if (Objects.nonNull(choooseChannel)){
            choooseChannel.writeAndFlush(msg);
        }
    }


}
