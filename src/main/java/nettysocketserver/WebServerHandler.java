package nettysocketserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import utils.ChannelWriteUtils;
import static utils.ChannelWriteUtils.webContainer;

@Slf4j
public class WebServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        /*log.info("client: "+ctx.channel()+" connect");
        webContainer.put("AppletID",ctx.channel());*/
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // WebSocket消息处理
        if (msg instanceof WebSocketFrame) {
            String webSocketInfo = ((TextWebSocketFrame) msg).text().trim();
            log.info("receive webSocket Info:{}",webSocketInfo);
            ChannelWriteUtils.channelWrite(webSocketInfo,ctx.channel(),"AppletID");
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("client: "+ctx.channel()+" disconnect");
        webContainer.remove("AppletID");
        log.info("current channel:"+(webContainer.entrySet()));
        super.channelInactive(ctx);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx.channel().isActive()){
            ctx.channel().close();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {

    }
}
