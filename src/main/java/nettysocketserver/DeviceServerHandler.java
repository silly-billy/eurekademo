package nettysocketserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class DeviceServerHandler extends SimpleChannelInboundHandler<Object> {

    public static Map container = new ConcurrentHashMap<String, List<String>>();


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("client: "+ctx.channel()+" connect");
        container.put(ctx.channel(),ctx.channel().remoteAddress());
        //container.entrySet().stream().forEach(c-> System.out.println(c));
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
        //并不触发，但不重写会报错很烦
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // WebSocket消息处理
        if (msg instanceof WebSocketFrame) {
            String webSocketInfo = ((TextWebSocketFrame) msg).text().trim();
            log.info("receive webSocket Info:{}",webSocketInfo);
            channelWrite(webSocketInfo,ctx.channel());
        }
        // Socket消息处理
        else{
            String socketInfo = String.valueOf(msg);
            log.info("receive socket Info:{}",socketInfo);
            var frame = new TextWebSocketFrame(socketInfo);
            channelWrite(frame,ctx.channel());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        /*if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            if (state == IdleState.ALL_IDLE) {
                // 在规定时间内没有收到客户端的上行数据, 主动断开连接
                ctx.disconnect();
                log.info("心跳检测触发，socket连接断开！");
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }*/
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx.channel().isActive()){
            ctx.channel().close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("client: "+ctx.channel().remoteAddress()+" disconnect");
        container.remove(ctx.channel());
        log.info("current channel:"+(container.entrySet()));
        super.channelInactive(ctx);
    }

    private boolean isActive(){
        //todo 查询同一pos机代码的channel是否存活
        if (container.size() != 2){
            return false;
        }
        return true;
    }

    private Channel queryPosChannel(Channel channel){
        //todo 查询对于POS机的连接
        if(isActive()){
            List<Object> channels = (List)container.keySet().stream().filter(f -> f != channel)
                    .collect(Collectors.toList());
            return (Channel) channels.get(0);
        }
        log.info("DeviceServerHandler.isActive return false");
        return null;
    }

    /**
     * @Description //TODO  webSocket和tcpSocket通信
     * @Date  2019/11/20
     * @Param msg channel
     * @return
     */
    private void channelWrite(Object msg,Channel channel){
        Channel posChannel = queryPosChannel(channel);
        if (Objects.nonNull(posChannel)){
            posChannel.writeAndFlush(msg);
        }
    }
}
