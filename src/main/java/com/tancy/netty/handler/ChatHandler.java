package com.tancy.netty.handler;

import com.tancy.SpringUtil;
import com.tancy.enums.MsgActionEnum;
import com.tancy.netty.ChatMsg;
import com.tancy.netty.DataContent;
import com.tancy.netty.UserChannelRel;
import com.tancy.service.UserService;
import com.tancy.utils.JsonUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;


public class ChatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static ChannelGroup users = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame) throws Exception {

        // 1. 获取客户端发来的信息
        String content = textWebSocketFrame.text();

        Channel currentChannel = channelHandlerContext.channel();

        DataContent dataContent = JsonUtils.jsonToPojo(content, DataContent.class);
        Integer action = dataContent.getAction();
        // 2. 判断消息类型，根据不同的类型来处理不同的业务

        if (action == MsgActionEnum.CONNECT.type) {
            // 2.1 当websocket第一次open的时候，初始化channel，把用户的channel和userid关联起来
            String senderId = dataContent.getChatMsg().getSenderId();
            UserChannelRel.put(senderId, currentChannel);

        } else if (action == MsgActionEnum.CHAT.type) {
            // 2.2 聊天类型的消息，把聊天记录保存到数据库，同时标记消息的签收状态【未签收】
            ChatMsg chatMsg = dataContent.getChatMsg();
            String msgText = chatMsg.getMsg();
            String recriverId = chatMsg.getReceiverId();
            String senderId = chatMsg.getSenderId();

            // 保存消息到数据库，并且标记为未签收
            // 不能直接使用 userService
            UserService userService = (UserService) SpringUtil.getBean("userServiceImpl");
            String msgId = userService.saveMsg(chatMsg);
            chatMsg.setMsgId(msgId);

            DataContent dataContent1 = new DataContent();
            dataContent.setChatMsg(chatMsg);

            // 发送消息
            // 从全局用户channel关系中获取接受方的channel
            Channel receiverchannel = UserChannelRel.get(recriverId);
            if (receiverchannel == null) {
                // channel 为空表示用户离线，推送消息（jpush,个推）
            } else {
                // 当channel不为空时，从ChannelGroup中查找对应的channel是否存在
                Channel findChannel = users.find(receiverchannel.id());
                if (findChannel != null) {
                    // 用户在线
                    receiverchannel.writeAndFlush(
                            new TextWebSocketFrame(
                                    JsonUtils.objectToJson(dataContent1)));

                } else {
                    // 用户离线 推送消息
                }
            }

        } else if (action == MsgActionEnum.SIGNED.type) {
            // 2.3 签收消息类型，针对具体消息进行签收，修改数据库中对应消息的签收状态【已签收】
            UserService userService = (UserService) SpringUtil.getBean("userServiceImpl");
            // 统一实现批处理
            // 扩展字段在signed类型中的信息中，代表需要去签收的信息id，逗号分隔
            String msgIdsStr = dataContent.getExtand();
            String msgIds[] = msgIdsStr.split(",");

            List<String> msgIdList = new ArrayList<>();
            // 通过循环去空，然后把值放到list中
            for (String mid : msgIds) {
                if (StringUtils.isNoneBlank(mid)) {
                    msgIdList.add(mid);
                }
            }

            System.out.println(msgIdList.toString());

            // 判断list不为null且不为空
            if (msgIdList != null && !msgIdList.isEmpty() && msgIdList.size()>0) {
                // 批量签收
                userService.updateMsgSigned(msgIdList);
            }

        } else if (action == MsgActionEnum.KEEPALIVE.type) {
            // 2.4 心跳类型的消息
            System.out.println("收到来自channel为[" + currentChannel + "]的心跳包");
        }

    }

    /**
     * 当客户端连接服务端之后（打开连接）
     * 获取客户端的channel，并且放到ChannelGroup中去管理
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        users.add(ctx.channel());
    }

    /**
     * 当触发handlerRemoved后，ChannelGroup会自动移除对应客户端的channel
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        users.remove(ctx.channel());
    }

    /**
     * channel发生异常
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception{
        cause.printStackTrace();
        //发生异常之后关闭channel，随后从channelGroup中移除
        ctx.channel().close();
        users.remove(ctx.channel());
    }
}
