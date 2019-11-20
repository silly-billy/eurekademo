package nettysocketserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import org.mortbay.util.MultiMap;
import org.mortbay.util.UrlEncoded;

public class CustomUrlHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 只针对FullHttpRequest类型的做处理，其它类型的自动放过
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uri = request.uri();
            int idx = uri.indexOf("?");
            if (idx > 0) {
                String query = uri.substring(idx + 1);
                // uri中参数的解析使用的是jetty-util包，其性能比自定义及正则性能高。
                MultiMap values = new MultiMap();
                UrlEncoded.decodeTo(query, values, "UTF-8");
                //todo get values Verification validity

                request.setUri(uri.substring(0, idx));
            }
        }
        ctx.fireChannelRead(msg);
    }
}
