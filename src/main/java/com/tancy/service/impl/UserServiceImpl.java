package com.tancy.service.impl;

import com.tancy.enums.MsgActionEnum;
import com.tancy.enums.MsgSignFlagEnum;
import com.tancy.enums.SearchFriendsStatusEnum;
import com.tancy.mapper.*;
import com.tancy.netty.ChatMsg;
import com.tancy.netty.DataContent;
import com.tancy.netty.UserChannelRel;
import com.tancy.pojo.FriendsRequest;
import com.tancy.pojo.MyFriends;
import com.tancy.pojo.Users;
import com.tancy.pojo.vo.FriendRequestVO;
import com.tancy.pojo.vo.MyFriendsVO;
import com.tancy.pojo.vo.UsersVO;
import com.tancy.utils.*;
import com.tancy.service.UserService;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.n3r.idworker.Sid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UsersMapper usersMapper;

    @Autowired
    private UsersMapperCustom usersMapperCustom;

    @Autowired
    private MyFriendsMapper myFriendsMapper;

    @Autowired
    private FriendsRequestMapper friendsRequestMapper;

    @Autowired
    private ChatMsgMapper chatMsgMapper;

    @Autowired
    private Sid sid;

    @Autowired
    private QrcodeUtils qrcodeUtils;

    @Autowired
    private FastdfsClient fastdfsClient;

    @Transactional(propagation = Propagation.SUPPORTS)
    @Override
    public boolean queryUsernameIsExist(String username) {

        Users user = new Users();
        user.setUsername(username);

        Users result = usersMapper.selectOne(user);

        return result != null ? true : false;
    }

    //查询的话，给事务一个SUPPORTS的级别
    @Transactional(propagation = Propagation.SUPPORTS)
    @Override
    public Users queryUserForLogin(String username, String pwd) {
        Example userExample = new Example(Users.class);
        Example.Criteria criteria = userExample.createCriteria();

        criteria.andEqualTo("username" , username);
        criteria.andEqualTo("password", pwd);

        Users result = usersMapper.selectOneByExample(userExample);

        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public Users saveUser(Users user) throws Exception {

        user.setUsername(user.getUsername());
        user.setPassword(MD5Utils.getMD5Str(user.getPassword()));
        user.setId(sid.nextShort());

        //设置二维码
        String qrCodePath = "F://user" + user.getId() + "qrcode.png";

        qrcodeUtils.createQrCode(qrCodePath, "waxin_qrcode:" + user.getUsername());

        MultipartFile qrcodeFile = FileUtils.fileToMultipart(qrCodePath);

        String qecodeUrl = fastdfsClient.uploadQRCode(qrcodeFile);

        user.setQrcode(qecodeUrl);
        user.setFaceImage("");
        user.setFaceImageBig("");
        user.setNickname(user.getUsername());
        usersMapper.insert(user);

        return user;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public Users updateUserInfo(Users users) {
        //根据users的主键，更新新的值，为null的则不会更新
        int i = usersMapper.updateByPrimaryKeySelective(users);
        System.out.println(i);

        System.out.println("更新数据库完毕");
        return queryUsersById(users.getId());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    @Override
    public Integer preconditionSearchFriends(String myUserId, String friendUsername) {
        Users user = queryUserInfoByUsername(friendUsername);

        if (user == null) {
            return SearchFriendsStatusEnum.USER_NOT_EXIST.status;
        }

        if (user.getId().equals(myUserId)) {
            return SearchFriendsStatusEnum.NOT_YOURSELF.status;
        }

        Example example = new Example(MyFriends.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("myUserId", myUserId);
        criteria.andEqualTo("myFriendUserId", user.getId());
        MyFriends myFriendsRes = myFriendsMapper.selectOneByExample(example);

        if (myFriendsRes != null) {
            return SearchFriendsStatusEnum.ALREADY_FRIENDS.status;
        }

        return SearchFriendsStatusEnum.SUCCESS.status;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    @Override
    public Users queryUserInfoByUsername(String friendUsername) {
        Example example = new Example(Users.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("username", friendUsername);
        return usersMapper.selectOneByExample(example);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public void sendFriendRequest(String myUserId, String friendUsername) {

        Users friend = queryUserInfoByUsername(friendUsername);

        Example example = new Example(FriendsRequest.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("sendUserId", myUserId);
        criteria.andEqualTo("acceptUserId", friend.getId());
        FriendsRequest friendRequest = friendsRequestMapper.selectOneByExample(example);

        if (friendRequest == null) {
            // 添加好友请求
            FriendsRequest request = new FriendsRequest();
            request.setId(sid.nextShort());
            request.setSendUserId(myUserId);
            request.setAcceptUserId(friend.getId());
            request.setRequestDateTime(new Date());

            friendsRequestMapper.insert(request);
        }

    }

    @Transactional(propagation = Propagation.SUPPORTS)
    @Override
    public List<FriendRequestVO> queryFriendRequestList(String acceptUserId) {
        return usersMapperCustom.queryFriendRequestList(acceptUserId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public void deleteFriendRequest(String sendUserId, String acceptUserId) {
        Example example = new Example(FriendsRequest.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("sendUserId", sendUserId);
        criteria.andEqualTo("acceptUserId", acceptUserId);

        friendsRequestMapper.deleteByExample(example);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public void passFriendRequest(String sendUserId, String acceptUserId) {
        saveFriends(sendUserId,acceptUserId);
        saveFriends(acceptUserId,sendUserId);
        deleteFriendRequest(sendUserId,acceptUserId);

        Channel sendChannel = UserChannelRel.get(sendUserId);
        if (sendChannel != null) {
            // 使用websocket主动推送消息到请求发起者，更新他的好友列表
            DataContent dataContent = new DataContent();
            dataContent.setAction(MsgActionEnum.PULL_FRIEND.type);

            sendChannel.writeAndFlush(
                    new TextWebSocketFrame(JsonUtils.objectToJson(dataContent)));
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    @Override
    public List<MyFriendsVO> queryMyFriends(String userId) {
        List<MyFriendsVO> myFriends = usersMapperCustom.queryMyFriends(userId);
        return myFriends;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public String saveMsg(ChatMsg chatMsg) {

        com.tancy.pojo.ChatMsg msgDB = new com.tancy.pojo.ChatMsg();
        String msgId = sid.nextShort();
        msgDB.setId(msgId);
        msgDB.setAcceptUserId(chatMsg.getReceiverId());
        msgDB.setSendUserId(chatMsg.getSenderId());
        msgDB.setCreateTime(new Date());
        msgDB.setSignFlag(MsgSignFlagEnum.unsign.type);
        msgDB.setMsg(chatMsg.getMsg());

        chatMsgMapper.insert(msgDB);

        return msgId;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public void updateMsgSigned(List<String> msgIdList) {
        usersMapperCustom.batchUpdateMsgSigned(msgIdList);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public List<com.tancy.pojo.ChatMsg> getUnReadMsgList(String acceptUserId) {

        Example example = new Example(com.tancy.pojo.ChatMsg.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("signFlag", 0);
        criteria.andEqualTo("acceptUserId", acceptUserId);
        List<com.tancy.pojo.ChatMsg> chatMsgs = chatMsgMapper.selectByExample(example);

        return chatMsgs;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    private void saveFriends(String sendUserId, String acceptUserId){

        MyFriends myFriends = new MyFriends();
        myFriends.setId(sid.nextShort());
        myFriends.setMyUserId(sendUserId);
        myFriends.setMyFriendUserId(acceptUserId);
        myFriendsMapper.insert(myFriends);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    private Users queryUsersById(String userId) {
        return usersMapper.selectByPrimaryKey(userId);
    }


}
