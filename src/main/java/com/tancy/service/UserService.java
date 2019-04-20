package com.tancy.service;

import com.tancy.netty.ChatMsg;
import com.tancy.pojo.Users;
import com.tancy.pojo.vo.FriendRequestVO;
import com.tancy.pojo.vo.MyFriendsVO;
import com.tancy.pojo.vo.UsersVO;

import java.util.List;

public interface UserService {

    //判断用户名是否存在
    boolean queryUsernameIsExist(String username);

    //返回查询结果
    Users queryUserForLogin(String username, String pwd);

    //用户注册
    Users saveUser(Users user) throws Exception;

    Users updateUserInfo(Users users);

    Integer preconditionSearchFriends(String myUserId, String friendUsername);

    Users queryUserInfoByUsername(String friendUsername);

    void sendFriendRequest(String myUserId, String friendUsername);

    List<FriendRequestVO> queryFriendRequestList(String acceptUserId);

    void deleteFriendRequest(String sendUserId, String acceptUserId);

    /**
     *  通过好友请求：保存好友，逆向保存好友，删除好友记录
     * @param sendUserId
     * @param acceptUserId
     */
    void passFriendRequest(String sendUserId, String acceptUserId);

    List<MyFriendsVO> queryMyFriends(String userId);

    /**
     *  保存聊天消息到数据库
     * @param chatMsg
     * @return
     */
    String saveMsg(ChatMsg chatMsg);

    /**
     * 批量签收消息
     * @param msgIdList
     */
    void updateMsgSigned(List<String> msgIdList);

    /**
     *  获取未签收消息列表
     * @param acceptUserId
     * @return
     */
    List<com.tancy.pojo.ChatMsg> getUnReadMsgList(String acceptUserId);
}
