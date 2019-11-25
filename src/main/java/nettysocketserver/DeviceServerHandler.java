package nettysocketserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import utils.ChannelWriteUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static utils.ChannelWriteUtils.channelWrite;
import static utils.ChannelWriteUtils.deviceContainer;

@Slf4j
public class DeviceServerHandler extends SimpleChannelInboundHandler<String> {


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        /*log.info("client: " + ctx.channel() + " connect");
        deviceContainer.put("AppletID", ctx.channel());*/
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //模拟登入发出的请求
        if ("login\r\n".equalsIgnoreCase(String.valueOf(msg)))
        {
            log.info("client: " + ctx.channel() + " connect");
            deviceContainer.put("AppletID", ctx.channel());
        }else {
            // Socket消息处理
            String socketInfo = String.valueOf(msg);
            log.info("receive socket Info:{}", socketInfo);
            var frame = new TextWebSocketFrame(socketInfo);
            channelWrite(frame, ctx.channel(), "AppletID");
        }

    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, String s) throws Exception {

    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            if (state == IdleState.ALL_IDLE) {
                // 在规定时间内没有收到客户端的上行数据, 主动断开连接
                ctx.disconnect();
                log.info("心跳检测触发，socket连接断开！");
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx.channel().isActive()) {
            ctx.channel().close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("client: " + ctx.channel().remoteAddress() + " disconnect");
        deviceContainer.remove(ctx.channel());
        log.info("current channel:" + (deviceContainer.entrySet()));
        super.channelInactive(ctx);
    }

}
