package com.notifyme.link;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.notifyme.NotifyMe;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * WebSocket endpoint phones connect to: ws://host:port/ws.
 * Uses the Netty 4.0 that ships with Minecraft 1.7.10, so no extra libraries are needed.
 * Netty threads never touch game state: every message is handed to the server thread.
 */
public class LinkServer {

    public static final String PATH = "/ws";

    private EventLoopGroup boss, workers;
    private Channel serverChannel;

    public void start(String bind, int port) throws InterruptedException {
        boss = new NioEventLoopGroup(1, named("NotifyMe-Link-Boss"));
        workers = new NioEventLoopGroup(2, named("NotifyMe-Link"));
        ServerBootstrap b = new ServerBootstrap().group(boss, workers)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new IdleStateHandler(75, 0, 0))
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(65536))
                        .addLast(new WebSocketServerProtocolHandler(PATH))
                        .addLast(new FrameHandler());
                }
            });
        serverChannel = b.bind(new InetSocketAddress(bind, port))
            .sync()
            .channel();
    }

    public void stop() {
        try {
            if (serverChannel != null) serverChannel.close()
                .awaitUninterruptibly(2000);
        } finally {
            if (boss != null) boss.shutdownGracefully();
            if (workers != null) workers.shutdownGracefully();
            serverChannel = null;
        }
    }

    private static ThreadFactory named(final String prefix) {
        final AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private static class FrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
            final Channel ch = ctx.channel();
            final String text = frame.text();
            NotifyMe.runOnServer(() -> LinkManager.onMessage(ch, text));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final Channel ch = ctx.channel();
            NotifyMe.runOnServer(() -> LinkManager.onClosed(ch));
            super.channelInactive(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) ctx.close();
            else super.userEventTriggered(ctx, evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            NotifyMe.LOG.debug("Link connection error: {}", cause.toString());
            ctx.close();
        }
    }
}
