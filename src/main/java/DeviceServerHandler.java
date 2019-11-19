
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeviceServerHandler extends SimpleChannelInboundHandler<Object> {


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("client: "+ctx.channel().remoteAddress()+" connect");
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
        }
        // Socket消息处理
        else{
            ByteBuf buff = (ByteBuf) msg;
            String socketInfo = buff.toString(CharsetUtil.UTF_8).trim();;
            log.info("receive socket Info:{}",socketInfo);
        }
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
        super.exceptionCaught(ctx, cause);
        if (ctx.channel().isActive()){
            ctx.channel().close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("client: "+ctx.channel().remoteAddress()+" disconnect");
        super.channelInactive(ctx);
    }
}
