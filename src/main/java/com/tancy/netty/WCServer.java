package com.tancy.netty;

import com.tancy.netty.handler.ChatHandler;
import com.tancy.netty.handler.HeartBeatHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

//单例实现
public class WCServer {

    private static volatile WCServer wcServer = null;

    private static ChannelFuture future;
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static ServerBootstrap server;

    private WCServer() { }

    public static WCServer getInstance() {
        if (wcServer == null){
            synchronized (WCServer.class){
                if (wcServer == null){
                    wcServer = new WCServer();

                    bossGroup = new NioEventLoopGroup();
                    workerGroup = new NioEventLoopGroup();

                    server = new ServerBootstrap();
                    server.group(bossGroup,workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                protected void initChannel(SocketChannel socketChannel) throws Exception {
                                    ChannelPipeline pipeline = socketChannel.pipeline();

                                    //#############  用于支持http协议
                                    //websocket基于http,所以需要http编解码器
                                    pipeline.addLast(new HttpServerCodec());
                                    //对写大数据流的支持
                                    pipeline.addLast(new ChunkedWriteHandler());
                                    //对httpMessage进行聚合，变成FullHttpRequest和FullHttpResponse
                                    pipeline.addLast(new HttpObjectAggregator(1024*64));


                                    //##############  增加心跳检测

                                    // 针对客户端，如果在1分钟时没有向服务端发送读写心跳（ALL），则主动断开
                                    // 如果是读空闲或者写空闲，不处理
                                    pipeline.addLast(new IdleStateHandler(8, 10, 60));
                                    // 自定义的空闲状态检测
                                    pipeline.addLast(new HeartBeatHandler());


                                    //##############  用于支持httpwebsocket
                                    pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));




                                    pipeline.addLast(new ChatHandler());
                                }
                            });
                }
            }
        }
        return wcServer;
    }

    public void start() {
        this.future = server.bind(8090);
        System.err.println("netty 启动成功");
    }
}
